# 이커머스 상품 랭킹 배치를 E2E 테스트하며 알게 된 것들

> Spring Batch + Testcontainers + 실 API 검증까지, 배치 파이프라인을 테스트하면서 겪은 문제와 발견.

---

## 1. 이 테스트는 무엇을 위한 것인가

쿠팡, 무신사 같은 이커머스에서 "인기 상품 TOP 100"은 단순한 조회가 아니다.
조회수, 좋아요, 매출, 취소를 조합한 **Score 계산**, 일간/주간/월간이라는 **시간 윈도우**, 그리고 실시간과 배치라는 **이중 경로**가 얽혀 있다.

```
[실시간 경로]  Kafka → Redis ZSET  →  daily 랭킹 (빠르지만 근사치)
[배치 경로]    DB 원장 → Spring Batch → MV 테이블 → weekly/monthly 랭킹 (느리지만 정확)
```

이 글에서 다루는 것은 **배치 경로의 E2E 테스트**다. "배치 Job이 돌았다"가 아니라, **"배치가 만든 데이터로 API가 의미 있는 결과를 내는가"** 를 검증했다.

테스트를 통해 확인하고 싶었던 질문:

- 3-Step 파이프라인(Cleanup → Partitioned Aggregate → Merge)이 정상 동작하는가?
- product_id 범위 분할 Partitioning이 데이터 누락 없이 집계하는가?
- 취소(cancel_amount)가 Score에 정확히 반영되는가?
- 같은 파라미터로 2회 실행해도 멱등성이 보장되는가?
- 데이터가 없거나 부분적일 때 Job이 안전하게 완료되는가?
- **일간/주간/월간 랭킹이 실제로 서로 다른 결과를 보여주는가?**

---

## 2. 배치 구조: 3-Step 파이프라인

```
Step 1: CleanupTasklet
  └─ 기존 period_key 데이터 삭제 (멱등성 보장)
  └─ 3일 이전 데이터 자동 퍼지

Step 2: Partitioned Aggregate (병렬)
  └─ product_id MIN~MAX 범위를 4파티션으로 분할
  └─ 각 파티션이 독립적으로 Score 계산 → staging 테이블 적재
  └─ Score = 0.1×LOG10(view+1)/7 + 0.2×LOG10(like+1)/7 + 0.7×LOG10(net_sales+1)/7

Step 3: Merge
  └─ staging에서 Global TOP 100 추출 → MV 테이블 적재
  └─ ROW_NUMBER() OVER (ORDER BY score DESC) LIMIT 100
```

핵심은 **Map-Reduce 패턴**이다. 각 파티션(Map)이 독립적으로 score를 계산하고, Merge 단계(Reduce)에서 전체 순위를 매긴다.

---

## 3. E2E 테스트: 10개 시나리오와 그 의미

### 테스트 환경

| 항목 | 값 |
|------|-----|
| DB | MySQL 8.0 (Testcontainers) |
| 프레임워크 | `@SpringBatchTest` + `@SpringBootTest` |
| 데이터 | 테스트마다 독립 시드 (JdbcTemplate INSERT) |

### 시나리오 목록

| # | 테스트 | 시나리오 | 검증 포인트 |
|---|--------|----------|-------------|
| 1 | **weeklySuccess** | 상품 150개 + 7일 메트릭 | 100건 적재, 1위 정확성, 전체 파이프라인 |
| 2 | **weeklyLessThan100** | 상품 30개 | LIMIT 100이지만 30건만 적재 |
| 3 | **monthlySuccess** | 상품 50개 + 30일 메트릭 | monthly 테이블 분기 |
| 4 | **idempotent** | 동일 파라미터 2회 실행 | 중복 없이 동일 결과 |
| 5 | **noData** | 메트릭 0건 | Job FAILED 아닌 COMPLETED |
| 6 | **partialData** | 7일 중 3일만 | 있는 만큼만 집계 |
| 7 | **cancellation** | 매출 200만/취소 150만 vs 매출 100만/취소 0 | 순매출 기준 순위 |
| 8 | **printRankingResults** | 20개 상품 × 30일 (5가지 패턴) | 일간/주간/월간 TOP 20 시각화 출력 |
| 9 | **largeScale** | 10만 상품 × 30일 (300만 행) | 4 Partition 병렬 집계, 파티션 균등 분배, 1위 정확성 |
| 10 | **partitionBenchmark** | gridSize=1 vs gridSize=4 | Partitioning 성능 효과 정량 측정 (2.1x 향상) |

7~10번 시나리오 중 처음 작성했을 때 기능 테스트(1~7) **모두 실패**했다. 테스트 프레임워크와의 충돌 때문이었다.

---

## 4. 테스트를 작성하며 발견한 문제들

### 문제 1: `@SpringBatchTest`가 private 메서드를 몰래 실행한다

```
No matching arguments found for method: runJob
```

`@SpringBatchTest`의 `JobScopeTestExecutionListener`는 테스트 클래스의 **모든 메서드**를 스캔한다. 접근 제어자와 무관하게 `getDeclaredMethods()`로 전부 가져온다. 이때 `JobExecution`을 반환하는 메서드를 찾으면 **인자 없이 호출을 시도**한다.

테스트 헬퍼 메서드를 이렇게 만들었다가 걸렸다:

```java
// AS-IS: 이렇게 하면 listener가 이 메서드를 발견하고 runJob() 호출 시도 → 실패
private JobExecution runJob(String scope) throws Exception { ... }
```

`JobExecution` 반환 타입이 탐지 조건이었으므로, 반환 타입만 바꾸면 해결된다:

```java
// TO-BE: BatchStatus를 반환하면 listener 스캔 대상에서 제외
private BatchStatus runJob(String scope) throws Exception { ... }
```

Spring Batch 내부의 `HippyMethodInvoker`(실제 클래스명이다)가 메서드 시그니처로 대상을 결정한다. 공식 문서에는 이 동작이 기술되어 있지 않다.

### 문제 2: `@JobScope` Partitioner Bean과 `@SpringBatchTest`의 충돌

```
SpelEvaluationException: EvaluationContext has no variable 'jobParameters'
```

Partitioner Bean에 `@Value("#{jobParameters['targetDate']}")`를 사용하려면 `@JobScope`가 필요하다. 하지만 `@JobScope`를 붙이면 `@SpringBatchTest`의 `JobScopeTestExecutionListener`와 충돌한다.

해결: Partitioner를 Bean이 아닌 **private 메서드**로 변경했다.

```java
// AS-IS: Bean으로 등록하면 @JobScope 필요 → listener와 충돌
@JobScope @Bean
public Partitioner productIdPartitioner(
    @Value("#{jobParameters['targetDate']}") String targetDate) { ... }

// TO-BE: 이미 @JobScope인 step 메서드에서 직접 호출
@JobScope @Bean("partitionedAggregateStep")
public Step partitionedAggregateStep(
    @Value("#{jobParameters['targetDate']}") String targetDate,
    @Value("#{jobParameters['scope']}") String scope) {
    return new StepBuilder(...)
        .partitioner("workerStep", createPartitioner(targetDate, scope))
        ...
}

private Partitioner createPartitioner(String targetDate, String scope) { ... }
```

`targetDate`, `scope`는 이미 `@JobScope`인 step 메서드의 파라미터로 주입받으므로 Partitioner가 별도 Bean일 필요가 없었다. 더 단순한 구조가 더 테스트하기 쉬운 구조이기도 했다.

---

## 5. 테스트 데이터 설계에서 발견한 함정

### "7일 데이터로는 주간과 월간의 차이를 증명할 수 없다"

처음에는 모든 테스트에 7일치 데이터만 시딩했다. 7개 시나리오는 모두 통과했지만, **시각화 테스트를 추가했을 때** 문제가 드러났다:

> 주간 랭킹과 월간 랭킹의 수치가 완전히 동일하다.

당연하다. 주간은 7일 윈도우, 월간은 30일 윈도우인데, 데이터가 7일밖에 없으니 양쪽 모두 같은 7일을 집계한 것이다.

이건 **테스트가 통과했지만 아무것도 증명하지 못한** 상태다. `monthlySuccess` 테스트는 "30일 윈도우로 쿼리한다"는 것만 확인했을 뿐, "30일 데이터가 7일 데이터와 다른 랭킹을 만든다"는 핵심 가정을 검증하지 않았다.

### 해결: 30일 데이터 + 6가지 트렌드 패턴

```
A) 급상승  (5%):  과거 23일 미미 → 최근 7일 폭발
B) 장기강자 (10%): 30일 꾸준히 높음
C) 하락추세 (5%):  과거 23일 높음 → 최근 7일 급락
D) 바이럴  (2%):  오늘 하루만 폭발
E) 취소높음 (3%):  매출 높지만 취소 50~70%
F) 일반   (75%): 보통 수준
```

이 패턴으로 30일 데이터를 시딩하자, 일간/주간/월간 랭킹이 **완전히 다른 TOP 20**을 보여주었다.

---

## 6. 가장 중요한 발견: 시간 윈도우가 랭킹을 결정한다

1,020개 상품에 30일 메트릭을 넣고 실제 API를 호출한 결과:

| 순위 | 일간 (Redis) | 주간 (MV) | 월간 (MV) |
|:----:|-------------|-----------|-----------|
| 1 | 아디다스 캠퍼스 올리브 **(바이럴)** | 나이키 에어리프트 카키 **(급상승)** | 반스 슬립온 올리브 **(장기강자)** |
| 2 | 살로몬 아웃펄스 네이비 **(바이럴)** | 컨버스 런스타하이크 그레이 **(급상승)** | 스투시 카고바지 화이트 **(장기강자)** |
| 3 | 뉴발란스 530 올리브 **(바이럴)** | 스투시 월드투어후디 카키 **(급상승)** | 리복 클럽C85 인디고 **(장기강자)** |

**같은 데이터, 같은 Score 공식인데 시간 윈도우만 달라도 1위부터 완전히 다르다.**

| 상품 트렌드 | 일간 순위 | 주간 순위 | 월간 순위 | 해석 |
|------------|:---------:|:---------:|:---------:|------|
| 바이럴 (오늘만 폭발) | 1위 | 100위 밖 | 100위 밖 | 1일치만 반영 |
| 급상승 (최근 7일 폭발) | 중위권 | 상위 | 100위 밖 | 과거 23일 미미 |
| 장기강자 (30일 꾸준) | 하위 | 하위 | 상위 | 꾸준함의 축적 |
| 하락추세 (과거 높고 최근 급락) | 하위 | 하위 | 상위 | 과거 실적이 30일에 반영 |

이것이 왜 중요한가?

"인기 상품"의 정의가 시간 윈도우에 따라 완전히 달라진다. **하나의 랭킹만 제공하면 어떤 관점은 반드시 누락된다.** 일간만 보여주면 장기 스테디셀러가 사라지고, 월간만 보여주면 바이럴 상품이 보이지 않는다. 이커머스에서 일간/주간/월간 랭킹을 별도 제공하는 이유가 여기에 있다.

---

## 7. Score 수식과 취소 반영

### Score 공식

```sql
  0.1 * LOG10(GREATEST(SUM(view_count), 0) + 1) / 7.0
+ 0.2 * LOG10(GREATEST(SUM(net_like_count), 0) + 1) / 7.0
+ 0.7 * LOG10(GREATEST(SUM(net_sales_amount), 0) + 1) / 7.0
+ UNIX_TIMESTAMP() * 1e-16
```

- **LOG10**: 조회수 100만과 200만의 차이가 1과 2만큼 크지 않게 만든다 (로그 스케일링)
- **가중치 0.1/0.2/0.7**: 매출 중심 랭킹 (view 10%, like 20%, sales 70%)
- **/7.0**: 일간 Score와 범위를 맞추기 위한 정규화
- **UNIX_TIMESTAMP * 1e-16**: Score가 동일할 때 최신 데이터를 우선하는 타이브레이커

### 취소 반영

```sql
SUM(pm.sales_amount - pm.cancel_amount_by_event_date) AS total_net_sales_amount
```

테스트 시나리오: 상품A(매출 100만, 취소 0) vs 상품B(매출 200만, 취소 150만).
상품B의 총매출이 2배지만 순매출은 50만이므로, 상품A(순매출 100만)가 1위가 된다. **매출 크기가 아니라 순매출이 순위를 결정**한다는 것을 테스트로 확인했다.

---

## 8. 배치 실행 성능

| 항목 | 값 |
|------|-----|
| 상품 수 | 1,020개 |
| 메트릭 행 수 | 30,600행 |
| 파티션 수 | 4 (product_id 범위 분할) |
| weekly 소요 시간 | 275ms |
| monthly 소요 시간 | 309ms |
| 적재 건수 | 100 (TOP 100) |

30,600행을 4파티션으로 나눠 병렬 처리한 결과, **300ms 이내**에 완료되었다.

### Partitioning 벤치마크: gridSize=1 vs gridSize=4

"Partitioning이 없었다면 단일 쿼리로 처리해야 하므로 데이터가 커질수록 차이가 벌어진다." — 이걸 실제로 측정해봤다.

10만 상품 × 30일(300만 행)에서 gridSize만 1과 4로 바꿔서 같은 데이터를 2회 실행한 결과:

| 구성 | weekly 소요 시간 | Worker당 상품 수 |
|------|----------------|--------------|
| gridSize=1 (단일 스레드) | **3,740ms** | 100,000 |
| gridSize=4 (4 Partition 병렬) | **1,763ms** | 25,000 |
| **향상률** | **2.1x** | |

이론적 상한은 4x지만, 실측은 2.1x다. 차이의 원인:

1. **Amdahl's Law**: Partitioner의 `SELECT DISTINCT product_id` 쿼리, mergeStep의 `ROW_NUMBER() OVER`, JobRepository 메타데이터 저장 등 **직렬 구간이 전체의 일부**를 차지한다.
2. **Testcontainers 환경 제약**: `innodb-buffer-pool-size=256M`으로 제한된 환경이므로, 프로덕션 MySQL에서는 더 큰 향상률이 기대된다.
3. **IO 경합**: 4개 Worker가 동시에 같은 MySQL 인스턴스에 접근하므로 디스크/메모리 경합이 발생한다.

그래도 **2.1x는 의미 있는 수치**다. 1일 1회 배치에서 3.7초와 1.8초의 절대적 차이는 크지 않지만, 데이터가 10배(100만 상품)로 늘어나면 37초 vs 18초로 벌어진다. 병렬화의 효과는 규모에 비례한다.

---

## 9. 실 환경 API 검증에서 발견한 것

E2E 테스트(Testcontainers)는 모두 통과했지만, **실제 commerce-api에서 weekly/monthly API를 호출하면 빈 결과**가 반환되었다.

원인: commerce-api가 **화요일에 시작**되었는데, MV Entity 클래스는 그 이후에 추가되었다. `ddl-auto: create`로 시작 시 테이블은 만들어졌지만, **런타임에 해당 Entity의 Repository 코드 자체가 빌드에 없었다.** 앱을 재빌드하고 재시작하자 정상 동작.

```
MvProductRank*.class → 빌드에 없음 → getFromMv() 호출되어도 쿼리 실행 안 됨
```

이건 E2E 테스트만으로는 잡을 수 없는 문제다. **E2E 테스트는 "코드가 맞는가"를 검증하지만, "배포된 버전이 최신인가"는 검증하지 않는다.** 실 환경에서 한 번 더 확인하는 것이 의미 있었던 이유다.

---

## 10. 정리: 이 테스트로 무엇을 알게 되었는가

### 기술적으로 확인한 것

| 검증 항목 | 결과 |
|-----------|------|
| 3-Step 파이프라인 정상 동작 | Cleanup → Partitioned Aggregate → Merge |
| product_id 범위 분할 Partitioning | 4파티션, 데이터 누락 없음 |
| scope 분기 (weekly/monthly) | 각각 다른 MV 테이블에 적재 |
| 멱등성 | Cleanup + RunIdIncrementer로 보장 |
| 빈 데이터 / 부분 데이터 | Job COMPLETED, 안전 처리 |
| 취소 반영 | 순매출 기준 순위 결정 |
| 시간 윈도우별 랭킹 차이 | 일간/주간/월간 TOP 20이 완전히 다름 |
| Partitioning 성능 효과 | gridSize=1 대비 gridSize=4가 2.1x 빠름 (10만 상품 기준) |

### 테스트 설계에서 배운 것

1. **"테스트가 통과한다"와 "의미 있는 것을 검증한다"는 다르다.** 7일 데이터로 월간 테스트를 돌리면 통과하지만 아무것도 증명하지 못한다.

2. **테스트 데이터의 다양성이 테스트의 품질을 결정한다.** 6가지 트렌드 패턴(급상승, 장기강자, 하락, 바이럴, 취소, 일반)을 설계한 후에야 시간 윈도우별 차이가 드러났다.

3. **Spring Batch 테스트 프레임워크에는 문서화되지 않은 동작이 있다.** `JobScopeTestExecutionListener`의 메서드 스캔, `HippyMethodInvoker`의 반환 타입 기반 탐지 등.

4. **E2E 테스트와 실 환경 검증은 다른 것을 잡는다.** 코드 정합성은 Testcontainers가, 배포 정합성은 실 환경 API 호출이 잡는다.

### 비즈니스 관점에서 확인한 것

시간 윈도우는 단순한 "기간 필터"가 아니다. **어떤 시간 윈도우를 선택하느냐가 "인기 상품"의 정의 자체를 바꾼다.** 오늘 SNS에서 터진 상품, 이번 주 꾸준히 팔린 상품, 한 달간 스테디셀러인 상품은 모두 "인기 상품"이지만, 하나의 랭킹으로는 세 관점을 동시에 담을 수 없다.

Lambda Architecture(실시간 Redis + 배치 MV)를 선택한 이유도 여기에 있다. 실시간 경로는 "지금 뜨는 상품"을, 배치 경로는 "기간 동안 검증된 상품"을 각각 담당한다. 두 경로가 서로 다른 것은 버그가 아니라 설계 의도이며, 이 테스트는 그 설계 의도가 실제로 동작하는지를 확인하는 과정이었다.
