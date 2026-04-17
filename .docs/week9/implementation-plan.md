# Volume 9 — Redis ZSET 기반 실시간 랭킹 파이프라인 구현 계획

## Context

인기 상품 랭킹을 DB 집계 쿼리로 뽑으면 동시 조회 시 같은 결과를 반복 계산하는 낭비가 발생한다.
이벤트 발생 시점에 미리 계산해두고, 조회 시에는 Redis ZSET에서 읽기만 하는 파이프라인을 구축한다.

**핵심 흐름:** 이벤트 발행 → Kafka → Consumer(ranking_metrics UPSERT) → SyncScheduler(dirty 조회 → SUM × weight → ZADD) → API(ZREVRANGE)

---

## Phase 0: 기반 구조 변경 (DB 스키마 + 공유 상수)

### 0-1. ranking_metrics 테이블 생성

기존 `product_metrics`는 누적 1 row 구조(UNIQUE product_id)이므로 일간/시간별 랭킹 산출 불가.
**기존 테이블은 유지하고**, 별도의 `ranking_metrics` 테이블을 신규 생성한다.

```sql
ranking_metrics (
  id             BIGINT AUTO_INCREMENT PK,
  product_id     BIGINT NOT NULL,
  metrics_date   DATE NOT NULL,
  metrics_hour   TINYINT NOT NULL,       -- 0~23
  view_count     INT DEFAULT 0,
  like_count     INT DEFAULT 0,
  order_revenue  DECIMAL(15,2) DEFAULT 0,
  dirty          BOOLEAN DEFAULT TRUE,
  created_at     DATETIME NOT NULL,
  updated_at     DATETIME NOT NULL,

  UNIQUE (product_id, metrics_date, metrics_hour),
  INDEX idx_dirty_date (dirty, metrics_date)
)
```

**파일 위치:** `commerce-streamer` 모듈 domain/ranking 패키지에 엔티티 생성

### 0-2. ranking_weight 테이블 생성

```sql
ranking_weight (
  id          BIGINT AUTO_INCREMENT PK,
  event_type  VARCHAR(32) NOT NULL,     -- VIEW, LIKE, ORDER
  weight      DECIMAL(5,4) NOT NULL,    -- 예: 0.1000
  updated_at  DATETIME NOT NULL,

  UNIQUE (event_type)
)
```

**[미정] 초기 weight 값:** qna.md에서 조회 0.1 / 좋아요 0.2 / 주문 0.7로 논의했으나, 이 값은 비즈니스 정책이므로 구현 시 논의 필요.

### 0-3. Redis 키 상수 클래스

`modules/redis`에 `RankingKeys` 상수 클래스 정의 (commerce-api, commerce-streamer 모두 의존).

```java
public class RankingKeys {
    public static final String DAILY_RANKING = "ranking:all:";     // + yyyyMMdd
    public static final String WEIGHT_CACHE = "ranking:weight:";   // + eventType
}
```

**관련 파일:**
- `modules/redis/src/main/java/com/loopers/config/redis/RankingKeys.java`

---

## Phase 1: VIEW 이벤트 Kafka 발행

### 현재 상태
- `ProductFacade.findById()` → `UserActionEvent(PRODUCT_VIEW)` ApplicationEvent 발행
- `UserActionEventListener`에서 로그만 출력 (Kafka 미연동)

### 변경 사항
`UserActionEventListener`에서 PRODUCT_VIEW 이벤트를 Kafka로 발행한다.

- **방식:** ApplicationEvent(비동기) → KafkaTemplate.send() 직접 호출
- **Outbox 미사용 이유:** 조회는 빈번하고 weight 0.1로 낮아 유실 허용. Outbox 트랜잭션 보장 불필요.
- **토픽:** 기존 `catalog-events` 토픽 재사용 vs 별도 토픽 분리

**[미정] VIEW 이벤트 토픽 분리 여부:**
- 기존 `catalog-events`에 합류: 단순하지만, VIEW 볼륨이 LIKE보다 훨씬 많아 파티션 부하 불균형 가능
- 별도 `view-events` 토픽: 볼륨 격리 가능하지만, 토픽/컨슈머 관리 비용 증가
- → 구현하면서 논의

**관련 파일:**
- `commerce-api/.../application/useraction/UserActionEventListener.java` — Kafka 발행 로직 추가
- `commerce-api/.../infrastructure/outbox/OutboxTopics.java` — 토픽 상수 추가 (필요시)

### 트레이드오프
- ApplicationEvent → Kafka 직접 발행은 **앱 크래시 시 이벤트 유실** 가능
- 조회 이벤트는 weight 0.1이므로 유실 감수 (qna.md Q1 정리 근거)

---

## Phase 2: ORDER_CREATED payload 확장

### 현재 상태
```java
// OutboxEventRecordListener.java
Map.of("orderId", event.orderId(), "userId", event.userId(),
       "totalAmount", event.totalAmount(), "itemCount", event.items().size())
```

items 배열이 payload에 포함되지 않아 Consumer에서 상품별 매출 집계 불가.

### 변경 사항
payload에 items 배열 추가:
```java
Map.of("orderId", event.orderId(), "userId", event.userId(),
       "totalAmount", event.totalAmount(),
       "items", event.items())  // List<OrderItemSnapshot>
```

`OrderItemSnapshot`에는 이미 `productId`, `price`, `quantity` 포함되어 있음.

**관련 파일:**
- `commerce-api/.../infrastructure/outbox/OutboxEventRecordListener.java` — payload 변경

### 주의사항
- 기존 ORDER_CREATED 메시지와의 하위 호환성: Consumer에서 items 필드 없는 메시지 처리 필요 (배포 시 순서)
- **[확인 필요]** 기존 OrderEventHandler가 items 없는 메시지를 처리하고 있으므로, 배포 순서에 따라 NPE 방지 로직 필요할 수 있음

---

## Phase 3: Kafka Consumer → ranking_metrics UPSERT

### 3-1. RankingMetrics 엔티티 + Repository

`commerce-streamer` 모듈에 생성:
- `domain/ranking/RankingMetrics.java` — 엔티티
- `domain/ranking/RankingMetricsRepository.java` — Repository 인터페이스
- `infrastructure/ranking/RankingMetricsJpaRepository.java` — JPA Repository
- `infrastructure/ranking/RankingMetricsRepositoryImpl.java` — 구현체

**UPSERT 패턴:** 기존 `ProductMetricsJpaRepository`의 `INSERT ... ON DUPLICATE KEY UPDATE` 패턴 차용.

```java
@Modifying
@Query(value = "INSERT INTO ranking_metrics (product_id, metrics_date, metrics_hour, view_count, like_count, order_revenue, dirty, created_at, updated_at) " +
       "VALUES (:productId, :date, :hour, :viewCount, 0, 0, true, NOW(), NOW()) " +
       "ON DUPLICATE KEY UPDATE view_count = view_count + :viewCount, dirty = true, updated_at = NOW()",
       nativeQuery = true)
void upsertViewCount(@Param("productId") Long productId, @Param("date") LocalDate date,
                     @Param("hour") int hour, @Param("viewCount") int viewCount);
```

### 3-2. RankingMetricsService

- `domain/ranking/RankingMetricsService.java`
- UPSERT 메서드: `incrementViewCount()`, `incrementLikeCount()`, `decrementLikeCount()`, `addOrderRevenue()`

### 3-3. Consumer/Handler 변경

**CatalogEventHandler 변경:**
기존 `productMetricsService` 호출에 **추가로** `rankingMetricsService` 호출.

```java
case "LIKE_CREATED" -> {
    productMetricsService.incrementLikeCount(productId);
    rankingMetricsService.incrementLikeCount(productId, today, currentHour);
}
case "LIKE_CANCELLED" -> {
    productMetricsService.decrementLikeCount(productId);
    rankingMetricsService.decrementLikeCount(productId, today, currentHour);
}
case "PRODUCT_VIEWED" -> {
    productMetricsService.incrementViewCount(productId);
    rankingMetricsService.incrementViewCount(productId, today, currentHour);
}
```

**OrderEventHandler 변경:**
items 배열 파싱 → 상품별 `addOrderRevenue()` 호출.

```java
case "ORDER_CREATED" -> {
    JsonNode items = node.get("items");
    for (JsonNode item : items) {
        long productId = item.get("productId").asLong();
        int price = item.get("price").asInt();
        int quantity = item.get("quantity").asInt();
        BigDecimal revenue = BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(quantity));
        rankingMetricsService.addOrderRevenue(productId, today, currentHour, revenue);
    }
    productMetricsService.incrementOrderCount(productId);  // 기존 로직 유지
}
```

**[미정] ORDER 이벤트의 productMetricsService 호출:**
- 기존 `incrementOrderCount`는 상품별이 아닌 주문 건수 기준이었음
- items 배열 도입으로 상품별로 호출해야 하는지, 아니면 기존 로직은 그대로 두고 ranking_metrics만 상품별로 처리할지 논의 필요

### 3-4. 배치 합산 최적화

현재 Consumer는 메시지를 개별 처리(`for (OutboxMessage message : messages)`)하고 있음.
productId별 합산 후 한 번에 UPSERT하면 Redis/DB 연산 횟수를 줄일 수 있음.

**[미정] 배치 합산 구현 여부:**
- Must-Have에서는 개별 처리로 시작하고, Nice-to-Have에서 배치 합산 최적화를 적용할 수 있음
- qna.md Q4에서 논의한 "배치 크기 ↑ → Redis 연산 ↓, 랭킹 지연 ↑" 트레이드오프
- 현재 설정: max.poll.records=3000, fetch.max.wait.ms=5s
- → 구현하면서 단건 처리로 시작할지, 처음부터 배치 합산할지 논의

---

## Phase 4: SyncScheduler — dirty → SUM × weight → ZADD

### 4-1. RankingWeight 엔티티 + Repository

`commerce-streamer` 모듈에 생성:
- `domain/ranking/RankingWeight.java` — 엔티티
- `domain/ranking/RankingWeightRepository.java` — Repository 인터페이스

### 4-2. Weight 캐시 (Redis)

- Consumer/Scheduler에서 weight 조회 시 Redis 우선, miss 시 DB fallback
- `ranking:weight:{eventType}` 키에 String 타입으로 캐시
- **[미정] TTL:** 짧은 TTL (60s? 300s?) — weight 변경 빈도에 따라 결정

### 4-3. RankingSyncScheduler

`commerce-streamer` 모듈 `application/ranking/` 패키지에 생성.

```
@Scheduled(fixedDelay = 5000)  // 5초 주기
1. dirty=true AND metrics_date=today인 행 조회
2. 해당 product_id들의 오늘치 시간대별 SUM 집계
3. SUM(view_count) × viewWeight + SUM(like_count) × likeWeight + SUM(order_revenue) × orderWeight
4. ZADD ranking:all:{yyyyMMdd} score productId (덮어쓰기)
5. 성공한 행만 dirty=false
```

**기존 스케줄러 패턴 차용:**
- `EntryTokenScheduler`, `OutboxRelayScheduler`와 동일한 패턴
- 최외곽 try-catch로 스케줄러 스레드 보호
- 개별 상품별 예외 처리 (하나 실패해도 나머지 계속)

**[미정] 스케줄러 주기:**
- requirement-analysis.md에서 5초로 설계했으나, 실제 부하에 따라 조정 가능
- "오늘의 인기 상품"은 수초 지연 체감 없음 (qna.md Q4 정리)

**[미정] SUM 집계 쿼리 성능:**
- dirty 상품이 많으면 `상품 수 × 24 row` SUM 쿼리 발생
- requirement-analysis.md 잠재 리스크에서 언급: "상품 수가 극단적이지 않으면 OK"
- → 구현 후 실제 쿼리 성능 측정 필요

### 트레이드오프
- **DB 우선 + 스케줄러 ZADD 방식의 장점:** 멱등성 확보 (ZADD 덮어쓰기), Redis-DB 이원화 원자성 문제 회피
- **단점:** 최대 5초 랭킹 반영 지연. "실시간 트렌딩"에는 부적합하지만, "오늘의 인기 상품"에는 충분

**관련 파일:**
- `commerce-streamer/.../application/ranking/RankingSyncScheduler.java`
- `commerce-streamer`의 `application.yml` — scheduler pool size 확인 (현재 commerce-api에만 2개 설정됨)

**[확인 필요] commerce-streamer의 TaskScheduler 설정:**
- 현재 commerce-streamer에 @Scheduled 사용 여부, pool size 설정 확인 필요
- 없으면 Spring Boot 기본값(pool size 1) 사용 → SyncScheduler 전용으로 충분할 수 있음

---

## Phase 5: 랭킹 조회 API

### 5-1. API 엔드포인트

```
GET /api/v1/rankings?date=yyyyMMdd&size=20&page=0
```

**응답:**
```json
{
  "meta": { "result": "SUCCESS" },
  "data": {
    "rankings": [
      { "rank": 1, "productId": 123, "productName": "...", "price": 29900, "score": 85.3 },
      ...
    ],
    "page": 0,
    "size": 20,
    "totalElements": 100
  }
}
```

### 5-2. 구현 흐름

1. `ZREVRANGE ranking:all:{date}` 로 상위 N개 productId + score 조회
2. productId 목록으로 상품 정보 조회 (IN 쿼리)
3. 애플리케이션 레벨에서 랭킹 순서대로 조합

**[미정] 상품 정보 조회 방식:**
- **Option A:** IN 쿼리로 DB 직접 조회 — 단순하고 항상 최신 데이터
- **Option B:** Redis 캐시 (`product:{productId}` → JSON) — DB 부하 감소, 캐시 무효화 필요
- qna.md Q5에서 논의: "랭킹 페이지의 약간의 staleness는 PDP에서 정확한 정보를 보여주는 트레이드오프로 허용 가능"
- → Must-Have에서는 IN 쿼리로 시작하고, 필요시 캐시 적용 논의

### 5-3. 상품 상세 조회 시 순위 포함

기존 `ProductFacade.findById()` 응답에 해당 상품의 랭킹 순위 추가.

```java
// Redis ZREVRANK로 순위 조회 (0-based → +1)
Long rank = redisTemplate.opsForZSet().reverseRank(rankingKey, productId.toString());
```

- 랭킹에 없으면 null 반환
- ProductInfo/ProductV1Dto에 `rank` 필드 추가

**관련 파일:**
- `commerce-api/.../interfaces/api/ranking/` — 새 패키지 (Controller, ApiSpec, Dto)
- `commerce-api/.../application/ranking/RankingFacade.java`
- `commerce-api/.../domain/ranking/RankingService.java` — Redis ZSET 조회 로직
- `commerce-api/.../application/product/ProductFacade.java` — findById에 rank 추가
- `commerce-api/.../application/product/ProductInfo.java` — rank 필드 추가

**[미정] 페이지네이션 방식:**
- ZREVRANGE는 offset 기반 (`start`, `stop`) → 커서 기반 페이지네이션은 자연스럽지 않음
- `ZREVRANGE key (page*size) ((page+1)*size - 1)` 로 offset 기반 처리
- `ZCARD`로 totalElements 조회

---

## Phase 6 (Nice-to-Have): 콜드 스타트 — Score Carry-Over

### 23:50 스케줄러

```
@Scheduled(cron = "0 50 23 * * *")  // 매일 23:50
1. 오늘 ZSET (ranking:all:{today}) 조회
2. 내일 키 (ranking:all:{tomorrow}) 에 score × 0.1 이월
3. 내일 키 TTL 2일 설정
```

**[미정] 이월 비율:**
- qna.md Q6에서 10%로 논의했으나, 비즈니스 정책으로 결정할 문제
- 너무 많으면 → 롱테일 독점, 너무 적으면 → 콜드 스타트 미해결
- → 설정값으로 외부화 (`application.yml`)

---

## 구현 시 확인/논의가 필요한 포인트 정리

### 미정 사항 (구현하면서 결정)

| # | 항목 | 선택지 | 논의 포인트 |
|---|------|--------|------------|
| 1 | VIEW 이벤트 토픽 | `catalog-events` 합류 vs `view-events` 분리 | VIEW 볼륨이 압도적 → 파티션 부하 불균형 |
| 2 | ORDER 이벤트의 기존 productMetrics 처리 | 기존 유지 vs 상품별로 변경 | items 배열 도입에 따른 기존 로직 정합성 |
| 3 | 배치 합산 최적화 시점 | 처음부터 vs Nice-to-Have | 단건 처리로 시작해도 UPSERT 원자성 보장됨 |
| 4 | Weight 초기값 | 조회 0.1 / 좋아요 0.2 / 주문 0.7 | 비즈니스 정책, 구현 시 임시값으로 시작 |
| 5 | Weight 캐시 TTL | 60s? 300s? | 변경 빈도에 따라 결정 |
| 6 | 상품 정보 조회 방식 | IN 쿼리 vs Redis 캐시 | Must-Have에서는 IN 쿼리, 이후 캐시 고려 |
| 7 | order_revenue에 log 정규화 적용 여부 | `price × quantity` vs `log(price × quantity)` | 매출 랭킹 vs 인기 랭킹 목적에 따라 |
| 8 | Score Carry-Over 이월 비율 | ~10% (설정값 외부화) | 비즈니스 정책 |

### 현재 구조의 트레이드오프

| 트레이드오프 | 감수하는 것 | 얻는 것 |
|-------------|------------|---------|
| DB 우선 + 스케줄러 ZADD | 최대 5초 랭킹 반영 지연 | 멱등성 확보, 이원화 원자성 문제 회피 |
| VIEW 이벤트 Outbox 미사용 | 앱 크래시 시 조회 이벤트 유실 | Outbox TX 비용 절감, 간결한 구현 |
| ranking_metrics 별도 테이블 | 기존 product_metrics와 데이터 중복 | 일간/시간별 집계 가능, weight 재계산 가능 |
| SyncScheduler 단일 장애점 | 스케줄러 멈추면 Redis 랭킹 stale | 구현 단순성 (분산 락 불필요) |
| 랭킹 페이지 IN 쿼리 | 매 조회마다 DB 접근 | 항상 최신 상품 정보, 캐시 무효화 불필요 |

### 구현 전 확인 필요 사항

1. **commerce-streamer TaskScheduler 설정:** @Scheduled 사용을 위한 pool size 설정 존재 여부
2. **ORDER_CREATED 하위 호환:** Consumer가 items 필드 없는 기존 메시지를 안전하게 무시할 수 있는지
3. **ranking_metrics DDL:** test 프로파일에서 JPA auto-generate로 생성되는지, 별도 처리 필요한지
4. **ZREVRANGE 페이지네이션:** Redis ZSET의 ZCARD가 삭제된 상품도 포함하므로 totalElements 정확성

---

## 파일 변경 목록 (예상)

### commerce-streamer (신규)
```
domain/ranking/
  ├── RankingMetrics.java           — 엔티티
  ├── RankingMetricsRepository.java — Repository 인터페이스
  ├── RankingWeight.java            — 엔티티
  ├── RankingWeightRepository.java  — Repository 인터페이스
  └── RankingMetricsService.java    — UPSERT 로직

infrastructure/ranking/
  ├── RankingMetricsJpaRepository.java
  ├── RankingMetricsRepositoryImpl.java
  ├── RankingWeightJpaRepository.java
  └── RankingWeightRepositoryImpl.java

application/ranking/
  ├── RankingSyncScheduler.java     — dirty → ZADD 동기화
  └── RankingWeightCacheService.java — weight Redis 캐시
```

### commerce-streamer (변경)
```
application/metrics/CatalogEventHandler.java  — PRODUCT_VIEWED, LIKE 처리 추가
application/metrics/OrderEventHandler.java    — items 파싱 + addOrderRevenue
```

### commerce-api (신규)
```
interfaces/api/ranking/
  ├── RankingV1Controller.java
  ├── RankingV1ApiSpec.java
  └── RankingV1Dto.java

application/ranking/
  └── RankingFacade.java

domain/ranking/
  └── RankingService.java           — Redis ZSET 조회
```

### commerce-api (변경)
```
infrastructure/outbox/OutboxEventRecordListener.java  — ORDER_CREATED payload 확장
application/useraction/UserActionEventListener.java   — VIEW → Kafka 발행
application/product/ProductFacade.java                — findById에 rank 추가
application/product/ProductInfo.java                  — rank 필드 추가
interfaces/api/product/ProductV1Dto.java              — rank 필드 추가
```

### modules/redis (신규)
```
config/redis/RankingKeys.java  — 랭킹 키 상수
```

---

## 테스트 계획

### 단위 테스트
- RankingMetrics 엔티티: dirty 플래그 동작
- Score 계산 로직: SUM × weight 정확성

### 통합 테스트 (Facade/Handler 레벨)
- `CatalogEventHandler` — PRODUCT_VIEWED → ranking_metrics UPSERT 검증
- `CatalogEventHandler` — LIKE_CREATED/CANCELLED → like_count 증감 검증
- `OrderEventHandler` — ORDER_CREATED items 파싱 → order_revenue 누적 검증
- `RankingSyncScheduler` — dirty 행 → ZADD → dirty=false 검증
- `RankingFacade` — ZREVRANGE + 상품 정보 조합 검증
- 멱등성 테스트: 동일 이벤트 재처리 시 중복 집계 없음

### E2E 테스트
- 상품 조회 → Kafka VIEW 이벤트 → ranking_metrics 반영 → 스케줄러 → Redis ZADD → 랭킹 API 조회
- 전체 파이프라인 end-to-end 검증

### 검증 방법
```bash
# 1. 인프라 시작
docker compose -f infra-compose.yml up -d

# 2. 전체 테스트
./gradlew test

# 3. commerce-streamer 테스트만
./gradlew :apps:commerce-streamer:test

# 4. commerce-api 테스트만
./gradlew :apps:commerce-api:test
```

---

## 구현 순서 (TDD 기반)

```
Phase 0 → Phase 3 → Phase 4 → Phase 2 → Phase 1 → Phase 5 → Phase 6
(기반)    (Consumer)  (Scheduler) (Payload) (VIEW발행) (API조회) (CarryOver)
```

**순서 근거:**
- Phase 0(기반)이 모든 Phase의 전제
- Phase 3(Consumer)을 먼저 구현해야 ranking_metrics에 데이터가 쌓임
- Phase 4(Scheduler)로 Redis에 데이터 동기화
- Phase 2(payload 확장)는 Consumer와 함께 진행 가능
- Phase 1(VIEW 발행)은 독립적으로 진행 가능
- Phase 5(API)는 Redis에 데이터가 있어야 검증 가능
- Phase 6(Carry-Over)는 Nice-to-Have
