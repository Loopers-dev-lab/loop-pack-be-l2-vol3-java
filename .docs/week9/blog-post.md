# Redis ZSET으로 실시간 랭킹 파이프라인 구축하기 — dirty 플래그, 점수 설계, Cache Stampede까지

---

## 시작하기 전에

"오늘의 인기 상품" 랭킹을 구현해달라는 요구사항을 받았을 때, 처음 떠오른 생각은 단순했다.

```sql
SELECT product_id, COUNT(*) as order_count
FROM orders
WHERE created_at >= CURDATE()
GROUP BY product_id
ORDER BY order_count DESC
LIMIT 20;
```

이 쿼리 하나면 되지 않을까? 그런데 잠깐, 이 쿼리를 초당 수백 명이 동시에 실행하면 어떻게 될까?

이 글은 그 질문에서 시작해서, Kafka → MySQL → Redis ZSET으로 이어지는 랭킹 파이프라인을 설계하고 구현하면서 마주친 세 가지 핵심 문제와 그 해결 과정을 정리한 글이다.

---

## 1. 왜 DB 집계 쿼리로 랭킹을 뽑으면 안 되는가

`GROUP BY`가 느린 건 어느 정도 알고 있었다. 그런데 "느리다"는 설명만으로는 왜 안 되는지 정확히 설명하기 어렵다.

### 같은 결과를 위한 반복 연산

`created_at` 인덱스가 있어도 `GROUP BY product_id`를 처리하려면 인덱스로 범위를 좁힌 다음, 그 결과셋을 **정렬하거나 해시 집계**해야 한다. 이 단계는 인덱스 레인지 스캔과는 별개다.

그런데 더 근본적인 문제가 있다. **랭킹은 조회 요청마다 바뀌지 않는다.** 주문이 들어오거나, 좋아요가 눌릴 때만 바뀐다. 그런데 1,000명이 동시에 랭킹 페이지를 열면 1,000번의 집계 연산이 DB에서 동시에 돌아간다. 이 1,000번의 결과는 거의 모두 동일하다.

같은 결과를 위해 같은 비싼 연산을 1,000번 반복하는 것 — 이게 낭비의 본질이다.

### 해결 방향

> 이벤트가 발생할 때 미리 계산해두고, 조회할 때는 그 결과만 읽어온다.

이 원칙 하나에서 전체 파이프라인이 나온다.

---

## 2. 파이프라인 전체 구조

```
사용자 행동 (조회/좋아요/주문)
    │
    ▼
Kafka (catalog-events / order-events)
    │
    ▼
commerce-streamer (Consumer)
    │
    ▼
ranking_metrics 테이블 UPSERT (dirty = true)
    │
    ▼
RankingSyncScheduler (5초 주기)
    │  dirty 행 조회 → SUM × weight → ZADD 덮어쓰기 → dirty = false
    ▼
Redis ZSET (ranking:all:{yyyyMMdd})
    │
    ▼
랭킹 API (ZREVRANGE → 상품 정보 조합)
```

이 흐름을 보면 자연스럽게 드는 질문이 있다. "Consumer에서 Kafka 메시지를 받으면 바로 Redis에 `ZINCRBY`로 쏘면 되지, 왜 DB를 거치는가?"

---

## 3. ZINCRBY를 포기한 이유 — DB는 SSOT, Redis는 서빙 레이어

처음 설계에서 가장 먼저 떠오른 방법은 Consumer가 이벤트를 받는 즉시 Redis ZSET에 점수를 누적하는 것이었다.

```
Consumer → ZINCRBY ranking:all:20260407 {productId} {delta}
```

`ZINCRBY`는 현재 점수를 읽지 않고 delta만 더한다. DB 조회 없이 O(log N)으로 랭킹이 업데이트된다. 단순하고 빠르다.

그런데 두 가지 문제가 있다.

**문제 1: weight를 바꾸면 이미 쌓인 점수를 수정할 수 없다**

`ZINCRBY`는 delta만 알지, 그 delta가 어떤 이벤트에서 왔는지 알지 못한다. 오전에 조회 weight가 0.1이었다가 오후에 0.05로 바뀌면, 오전에 쌓인 점수는 그대로다. 같은 날 오전/오후 기준이 다른 랭킹이 만들어진다.

**문제 2: Redis가 죽으면 랭킹을 복구할 수 없다**

Redis는 휘발성이다. 재시작하면 오늘의 이벤트 기록이 모두 사라진다. `ZINCRBY`로만 쌓았다면 복구할 방법이 없다.

### 선택: DB를 원천 데이터(SSOT)로

원시 데이터(조회수, 좋아요 수, 주문 매출)를 DB에 보관하고, Redis는 DB에서 계산된 점수의 서빙 레이어로만 쓴다. Redis가 죽어도 DB가 있으면 언제든 재계산해서 복구할 수 있다. Weight가 바뀌어도 오늘치 DB 데이터를 새 weight로 다시 계산해 ZADD하면 된다.

```java
// 스케줄러의 점수 계산
private double calculateScore(RankingMetricsSummary summary,
                              BigDecimal viewWeight, BigDecimal likeWeight, BigDecimal orderWeight) {
    double viewScore  = Math.log1p(summary.totalViewCount()) * viewWeight.doubleValue();
    double likeScore  = Math.log1p(summary.totalLikeCount()) * likeWeight.doubleValue();
    double orderScore = Math.log1p(summary.totalOrderRevenue().doubleValue()) * orderWeight.doubleValue();
    return viewScore + likeScore + orderScore;
}
```

Weight는 코드에 하드코딩하지 않고 `ranking_weight` 테이블에 저장한다. 코드 배포 없이 비즈니스 정책으로 조정 가능하다.

---

## 4. dirty 플래그 패턴 — 이원화 저장의 원자성 딜레마

DB와 Redis에 동시에 저장하는 구조에서 가장 먼저 맞닥뜨리는 문제가 있다.

> MySQL 트랜잭션과 Redis 연산은 하나의 트랜잭션에 묶일 수 없다.

Consumer가 DB UPSERT와 Redis ZADD를 순서대로 실행할 때, DB 저장은 성공했는데 Redis 저장에서 에러가 나면? 반대로 Redis는 업데이트됐는데 DB 커밋이 실패하면? 두 저장소가 불일치 상태에 빠진다.

### 해결: 재처리가 안전한 구조

"원자성을 보장할 수 없다면, 실패해도 재처리로 수렴하는 구조를 만들자."

Consumer는 DB에만 저장하고 `dirty = true`로 표시한다. Redis 동기화는 별도 스케줄러 책임이다.

```sql
-- Consumer가 이벤트를 소비할 때마다 실행되는 UPSERT
INSERT INTO ranking_metrics
  (product_id, metrics_date, metrics_hour, view_count, like_count, order_revenue, dirty, ...)
VALUES
  (:productId, :date, :hour, :viewCount, 0, 0, true, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  view_count = view_count + :viewCount,
  dirty = true,
  updated_at = NOW();
```

그리고 5초마다 스케줄러가 `dirty = true`인 행을 조회해서 점수를 계산하고 Redis에 ZADD한다. ZADD는 덮어쓰기(overwrite)이므로 같은 상품을 여러 번 실행해도 결과가 같다 — 완전한 멱등성이 보장된다.

```java
@Scheduled(fixedDelay = 5000)
public void sync() {
    LocalDate today = LocalDate.now();

    // dirty=true 행을 productId 기준으로 그룹핑
    Map<Long, List<Integer>> dirtyByProduct =
            rankingMetricsService.findDirtyEntriesGroupedByProduct(today);

    if (dirtyByProduct.isEmpty()) return;

    for (Map.Entry<Long, List<Integer>> entry : dirtyByProduct.entrySet()) {
        Long productId = entry.getKey();
        List<Integer> dirtyHours = entry.getValue();

        // 오늘 전체 시간대 SUM → 일간 랭킹 ZADD (덮어쓰기)
        RankingMetricsSummary daily = rankingMetricsService.sumByProductIdAndDate(productId, today);
        double dailyScore = calculateScore(daily, viewWeight, likeWeight, orderWeight);
        masterRedisTemplate.opsForZSet().add(dailyKey, String.valueOf(productId), dailyScore);

        // 해당 시간대만 ZADD → dirty 해제
        for (int hour : dirtyHours) {
            // ... 시간별 랭킹 처리 ...
            rankingMetricsService.clearDirtyByHour(productId, today, hour);
        }
    }
}
```

이 패턴의 핵심은 `clearDirtyByHour`가 Redis 저장 **성공 후**에만 호출된다는 것이다. 스케줄러가 중간에 죽어도 `dirty = true` 행은 그대로 남아 있다가 재시작 후에 다시 처리된다.

```
DB에만 저장 (dirty=true)
    → 스케줄러가 주기적으로 감지
    → ZADD 성공 후 dirty=false
    → 실패하면 다음 주기에 재처리
```

재처리가 안전한 구조 = 원자성이 없어도 최종적으로 일관성에 수렴한다.

### weight 변경 시 오늘 전체 재계산

Weight가 바뀌면 이미 ZSET에 들어간 오늘치 점수를 어떻게 할 것인가? 가장 간단한 방법은 오늘치 모든 행을 `dirty = true`로 마킹하는 것이다.

```sql
UPDATE ranking_metrics SET dirty = true WHERE metrics_date = CURDATE();
```

다음 스케줄러 실행 시 모든 행을 새 weight로 재ZADD한다. 오늘 랭킹 전체에 일관된 weight가 적용된다.

"3,000행(500 상품 × 6시간)을 한 번에 재처리하면 API가 느려지지 않을까?" — 이 우려는 부하 테스트로 검증했다. 뒤에서 다룬다.

---

## 5. 점수 설계 — 무엇이 인기인가를 수치로 정의하기

### 이벤트별 가중치

조회, 좋아요, 주문은 구매 의도 강도가 다르다.

- **조회**: 사용자가 관심을 가졌다. 하지만 살 의사는 없을 수도 있다.
- **좋아요**: 나중에 사고 싶다는 신호다.
- **주문**: 실제로 지갑을 열었다.

의도 강도 순서대로 weight를 부여한다: 주문 > 좋아요 > 조회. 구체적인 값(0.7, 0.2, 0.1 등)은 기술 문제가 아니라 비즈니스 정책이다. 그래서 코드가 아닌 DB 테이블(`ranking_weight`)에 저장했다.

### log 정규화가 필요한 이유

주문 점수를 `price × quantity`(매출 절대값)로 쓰면 어떻게 될까?

100만원짜리 상품 1건 주문 = 1,000,000점
1,000원짜리 상품 1,000건 주문 = 1,000,000점

절대값 기준으로는 동점이다. 그런데 50만원짜리 상품이 2건 팔리면? 1,000,000점. 역시 동점. 100만원짜리 상품이 10건 팔리면 10,000,000점으로 순식간에 압도적 1위가 된다.

고가 상품이 소수만 팔려도 저가 상품의 대량 판매를 압도하는 구조 — 이것이 매출 절대값의 문제다. 랭킹의 목적이 "어떤 상품이 많은 사람에게 관심받는가"라면 이 구조는 의도와 맞지 않는다.

`log(1 + x)`를 적용하면 극단값이 억제된다.

| 매출 | 절대값 | log(1 + x) |
|------|--------|-----------|
| 1,000 | 1,000 | 6.9 |
| 100,000 | 100,000 | 11.5 |
| 10,000,000 | 10,000,000 | 16.1 |

매출이 10,000배 차이나도 log 점수는 2.3배 차이다. 극단값을 압축해 다양한 상품이 랭킹 상위권에 공존할 수 있게 만든다.

```java
double orderScore = Math.log1p(summary.totalOrderRevenue().doubleValue()) * orderWeight.doubleValue();
```

`Math.log1p(x)`는 `log(1 + x)`다. `x = 0`(주문이 없는 상품)일 때 `log(1) = 0`으로 안전하게 처리된다.

---

## 6. 랭킹 API — 캐시 없이 짰다가 부하 테스트에서 발견한 것

### Before: Redis를 쓰는데 왜 느리지?

파이프라인이 완성되고 랭킹 API를 구현했다. 구조는 간단했다.

```
GET /api/v1/rankings
  → Redis ZSET에서 상위 N개 productId 조회
  → DB IN 쿼리로 상품명, 가격 조회
  → 응답 조합
```

Redis에서 productId를 읽어오니 빠를 것이라 생각했다. k6로 100 VU, 60초 부하 테스트를 돌렸다.

**결과 (캐시 없음):**

| 지표 | 값 | 기준 |
|------|-----|------|
| P50 | 72.89ms | — |
| P95 | 172.47ms | ❌ 목표(50ms) 3.4배 초과 |
| P99 | 374.53ms | ❌ 목표(100ms) 3.7배 초과 |
| RPS | 1,160/s | — |

Redis를 쓰는데 왜 이렇게 느린가? 코드를 다시 봤다.

```java
// RankingFacade — 문제의 코드
public RankingResult findDailyRanking(LocalDate date, int page, int size) {
    List<ZSetOperations.TypedTuple<String>> tuples =
            rankingService.findDailyRanking(date, offset, size);  // Redis 읽기

    // 여기가 문제: 매 요청마다 DB를 친다
    Map<Long, Product> productMap = productService.findAllByIds(productIds)  // DB IN 쿼리
    Map<Long, String> brandNameMap = brandService.findNamesByIds(brandIds);  // DB IN 쿼리
}
```

`ProductFacade`에는 Cache-Aside가 적용되어 있었지만, `RankingFacade`는 `ProductFacade`가 아닌 `ProductService`를 직접 호출하고 있었다. Redis가 아무리 빨라도 매 요청마다 DB IN 쿼리가 두 번 붙으니 P95 50ms는 구조적으로 불가능했다.

### After: 결과 캐시 도입

조립된 랭킹 페이지 결과 전체를 Redis에 캐싱한다.

```java
@Transactional(readOnly = true)
public RankingResult findDailyRanking(LocalDate date, int page, int size) {
    String cacheKey = "loopers:ranking:daily:" + date + ":p" + page + ":s" + size;

    // 캐시 히트 → 즉시 반환
    Optional<RankingResult> cached = rankingCacheRepository.get(cacheKey);
    if (cached.isPresent()) {
        return cached.get();
    }

    // 캐시 미스 → ZSET 조회 + DB IN 쿼리 + 결과 조합
    RankingResult result = loadDailyRankingFromSource(date, page, size);

    // 조합 결과를 30초 캐시
    rankingCacheRepository.save(cacheKey, result);
    return result;
}
```

읽기는 Replica로 분산하고, 쓰기는 Master에만 한다.

```java
public RankingCacheRepositoryImpl(
        RedisTemplate<String, String> defaultRedisTemplate,       // REPLICA_PREFERRED
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate,
        ...
) {
    this.readTemplate  = defaultRedisTemplate;
    this.writeTemplate = masterRedisTemplate;
}
```

**결과 (캐시 적용 후):**

| 지표 | Before | After | 개선율 |
|------|--------|-------|--------|
| P50 | 72.89ms | **37ms** | -49% |
| P95 | 172.47ms ❌ | **82ms** ❌ | -52% |
| P99 | 374.53ms ❌ | **155ms** ❌ | -59% |
| RPS | 1,160/s | **2,285/s** | +97% |

RPS는 2배 상승하고, 평균 응답시간은 절반으로 줄었다. 하지만 P95/P99가 여전히 목표를 초과한다. 왜?

### Cache Stampede — 캐시가 만들어낸 새로운 문제

P99에서 여전히 스파이크가 발생하는 이유를 추적했다.

**원인:** TTL 30초가 만료되는 순간, 대기 중이던 수십 개의 요청이 동시에 캐시 미스를 맞는다. 모두 DB IN 쿼리를 실행하려고 달려든다. 이 순간만 DB 부하가 폭발하고, 응답 시간이 튄다.

```
캐시 만료 → VU 50개가 동시에 캐시 미스
         → 50개가 모두 DB IN 쿼리 실행
         → 일시적 레이턴시 스파이크
         → 50개 모두 캐시 저장 (49개는 낭비)
```

이것이 Cache Stampede(캐시 스탬피드)다. 캐시 도입이 해결한 문제(매 요청 DB 조회)와는 다른 새로운 문제다.

완전한 해결책은 두 가지다:

- **Singleflight**: 동일 키에 대해 진행 중인 DB 조회가 있으면 나머지 요청은 결과를 기다렸다가 공유한다. DB 조회가 정확히 1회만 발생한다.
- **Probabilistic Early Expiration(PER)**: TTL 만료 전에 확률적으로 미리 갱신해 만료 순간에 다수가 몰리지 않게 한다.

이번 구현에서는 현재 트래픽 규모에서 P95 82ms가 서비스에 치명적이지 않다고 판단해 수용했다. 트래픽이 증가하거나 레이턴시 SLA가 더 타이트해지면 Singleflight를 도입할 것이다.

> 캐시는 문제를 해결하지만, 동시에 새로운 문제를 만든다. 부하 테스트 없이는 보이지 않는다.

---

## 7. 두 레이어가 독립적으로 동작한다는 것의 의미

이 파이프라인에는 사실 두 개의 캐시 레이어가 있다.

```
레이어 1: Redis ZSET (ranking:all:{date})
  - 이벤트 기반으로 점수가 누적되는 랭킹 원천 데이터
  - TTL 2일, 스케줄러가 ZADD 덮어쓰기

레이어 2: Result Cache (loopers:ranking:daily:*:p*:s*)
  - 조립된 랭킹 페이지 결과 (상품명, 가격 포함)
  - TTL 30초, 캐시 미스 시 ZSET + DB 조합
```

이 두 레이어가 독립적으로 동작한다는 것이 중요한 의미를 갖는다.

weight 변경 시 스케줄러가 3,000행을 한 번에 재처리하는 시나리오를 k6로 측정했다.

**시나리오:** 사전에 500 상품 × 6시간 = 3,000행의 데이터를 적재 후, `UPDATE ranking_metrics SET dirty=true WHERE metrics_date=CURDATE()` 실행. 동시에 랭킹 API에 100 VU 부하.

**결과:**

| 지표 | 기준선(캐싱 후) | 스파이크 포함 | 변화 |
|------|--------------|------------|------|
| Avg  | 43ms | 44ms | **+2%** |
| P95  | 82ms | 91ms | **+11%** |
| P99  | 155ms | 159ms | **+3%** |
| RPS  | 2,285/s | 2,240/s | **-2%** |

3,000행 재처리 중에도 랭킹 API P99는 155ms → 159ms, 사실상 무변화다.

이유는 명확하다. 스케줄러는 레이어 1(ZSET)을 갱신하지만, API는 레이어 2(Result Cache)에서 서빙된다. 스케줄러가 아무리 바빠도 API는 이미 캐시된 결과를 돌려준다. 두 레이어 사이에 격벽이 있는 셈이다.

단, 이 독립성에는 트레이드오프가 있다. weight 변경 이후 사용자가 새 랭킹을 보기까지의 최대 지연은:

```
스케줄러 주기(5초) + Result Cache TTL(30초) = 최대 35초
```

ZSET이 갱신돼도 Result Cache가 만료되어야 비로소 새 점수 기반 랭킹이 서빙된다. 랭킹 가중치 변경은 실시간이 아닌 운영 정책 변경이므로 35초 반영 지연은 허용 범위라 판단했다.

---

## 8. 키 전략과 TTL — 날짜별 키 분리의 이유

마지막으로 짧지만 중요한 포인트 하나.

```
ranking:all:20260407  ← 날짜별 분리
ranking:all:20260408
```

`ranking:all` 하나에 계속 누적하면 어떻게 될까? 4월 1일에 폭발적으로 인기 있었던 상품이 4월 7일에는 조용해도 누적 점수가 압도적이라 계속 1위다. 일간 랭킹이 아니라 역대 누적 랭킹이 된다.

날짜별로 키를 분리하면 오늘치 데이터만으로 오늘 랭킹을 계산한다.

TTL은 1일이 아니라 2일로 설정했다. 이유가 두 가지다.

첫 번째는 전날 랭킹 조회다. 4월 7일 오전에 "어제(4월 6일) 랭킹이 궁금하다"는 기능 요구가 생길 수 있다. TTL을 1일로 하면 4월 6일 키는 자정에 생성되어 4월 7일 자정에 만료된다. 4월 7일 오전에 조회하면 이미 사라진 키다. 2일로 설정하면 하루가 지나도 전날 랭킹을 조회할 수 있다.

두 번째 이유는 다음 섹션에서 이어진다.

---

## 9. 콜드 스타트 — 자정에 랭킹이 초기화될 때 생기는 문제

날짜별 키 분리의 한 가지 부작용이 있다. **자정마다 오늘의 ZSET이 비어있는 상태에서 시작한다.**

### 자정 직후의 랭킹은 믿을 수 있는가

자정 0시 0분, 오늘의 ZSET(`ranking:all:20260408`)은 텅 비어 있다. 이 상태에서 상품 A의 주문이 0시 1분에 1건 들어오면, A는 점수가 생겨 랭킹 1위가 된다.

이게 "오늘의 인기 상품"으로 서빙되어도 괜찮은가? 자정 직후 1건의 주문이 하루 전체 랭킹의 방향을 결정하는 셈이다. 오전 데이터가 충분히 쌓이기 전까지 랭킹은 의미 있는 신호를 주지 못한다.

이것이 **콜드 스타트(Cold Start)** 문제다.

### Score Carry-Over — 어제의 인기가 오늘의 출발점이 된다

해결책은 날짜가 바뀌기 전에 오늘 ZSET에 미리 점수를 심어두는 것이다. 23:50에 스케줄러가 실행되어, 오늘 랭킹 상위 100개 상품의 점수를 일부 내일 키에 이월한다.

```
23:50 Carry-Over 스케줄러:
  ZREVRANGE ranking:all:{오늘} 0 99
  → 각 상품의 score × 0.1
  → ZADD ranking:all:{내일} {이월 점수}
```

자정이 지나면 내일 ZSET에 이미 100개 상품의 기준 점수가 존재한다. 오전에 소수의 주문이 들어와도 이미 형성된 점수 분포 위에서 경쟁하게 되어 랭킹이 급격히 뒤집히지 않는다.

### 이월 비율 — 얼마나 넘겨야 하는가

이월 비율을 결정할 때 두 극단을 먼저 생각해보자.

**이월이 너무 많으면 (예: 80%):** 어제 인기 상품이 오늘도 압도적인 기준 점수를 들고 출발한다. 신규 상품이나 새롭게 부상하는 상품이 역전하기 어렵다. 랭킹이 고착화된다.

**이월이 너무 적으면 (예: 1%):** 사실상 아무 기준점이 없는 것과 같다. 자정 직후 우연히 먼저 주문된 상품이 일시적으로 1위를 차지하는 콜드 스타트 문제가 그대로 남는다.

10% 이월을 선택했다. 어제 인기가 오늘의 시작점이 되지만, 오늘 실제 데이터가 빠르게 쌓이면 자연스럽게 역전된다. 정확한 비율은 기술 문제가 아니라 비즈니스 정책이다.

### 왜 전체 ZSET이 아니라 Top 100만 이월하는가

이월 대상을 전체 상품으로 확장하면 스케줄러 비용이 상품 수에 비례해 선형으로 늘어난다. 그런데 대부분의 서비스는 상위 20~100개 상품만 랭킹 페이지에 표시한다. 101위 이하 상품의 Carry-Over는 UX에 아무런 영향을 주지 않는다.

`ZREVRANGE 0 99`로 상위 100개만 가져오면 복잡도가 O(log N + 100)으로 고정된다. 상품이 10만 개가 되어도 스케줄러 실행 비용이 변하지 않는다.

### Carry-Over 점수는 어떻게 사라지는가

이월된 점수는 영구적이지 않다. 오늘 실제 이벤트가 쌓이면 스케줄러의 ZADD가 **덮어쓰기**로 오늘치 점수를 새로 계산한다. Carry-Over 값은 자연스럽게 실제 점수로 교체된다.

```
23:50  → Carry-Over: 상품 A score = 150점 (어제 1,500점 × 0.1)
00:05  → 오전 0시~1시 이벤트 반영, ZADD 덮어쓰기: 상품 A score = 12점 (오늘 실데이터)
10:00  → 오전 이벤트 누적, ZADD 덮어쓰기: 상품 A score = 830점
```

이월 점수가 실제 점수보다 크거나 작더라도, 스케줄러가 계속 덮어쓰므로 최종적으로 오늘 실데이터 기반 점수가 정착한다.

여기서 TTL 2일의 두 번째 이유가 나온다. 23:50 스케줄러가 `ZREVRANGE ranking:all:{오늘}`로 오늘 키를 참조해야 하는데, 오늘 키의 TTL이 자정에 딱 만료되면 스케줄러 실행 시점에 이미 키가 없을 수 있다. 2일 TTL이면 안전하게 참조할 수 있다.

---

## 마치며

이번 구현을 통해 가장 크게 깨달은 세 가지를 정리하면:

**1. DB는 SSOT, Redis는 서빙 레이어**
Redis를 SSOT로 쓰면 장애와 정책 변경에 취약해진다. DB에 원시 데이터를 보관하고 Redis는 읽기 전용 서빙 레이어로만 쓰면, 장애 복구와 재계산 모두 DB 하나로 해결된다.

**2. 원자성이 없으면 재처리가 안전한 구조를 만들자**
MySQL과 Redis를 동시에 트랜잭션으로 묶을 수 없다는 제약을 피하는 대신, dirty 플래그로 "아직 동기화 안 됨"을 표시하고 스케줄러가 멱등하게 재처리하는 구조를 만들었다. 재처리 가능성을 설계에 내재화하면 원자성 제약을 우회할 수 있다.

**3. 캐시를 붙이면 새로운 문제가 생기고, 부하 테스트 전에는 보이지 않는다**
매 요청 DB 조회를 없애려고 Cache-Aside를 도입했는데, TTL 만료 순간의 Cache Stampede라는 새로운 문제가 생겼다. 이것은 코드 리뷰에서는 보이지 않는다. 부하 테스트를 돌려서 P99 스파이크 패턴을 보고서야 원인을 파악했다.

완전히 완성된 시스템이라기보다, 각 결정에서 트레이드오프를 의식하고 "지금 이 규모에서 허용 가능한가"를 기준으로 판단했다. Cache Stampede의 Singleflight 해결, 토픽 분리 기준 — 이것들은 트래픽이 더 커지면 꺼낼 카드들이다.
