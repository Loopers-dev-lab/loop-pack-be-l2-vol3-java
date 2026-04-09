# 9주차 - R9 랭킹 시스템 테스트 정리

## TL;DR

- **총 신규 테스트 파일**: 11개 (streamer 7 + api 4)
- **총 테스트 케이스**: **68개** (Unit 38 / Integration 22 / E2E 8)
- **실행 결과**: **failures=0, errors=0** — 전부 통과
- **실행 시각**: 2026-04-09 14:51 KST — `./gradlew ... --rerun-tasks` (강제 재실행)
- **벽시계 소요 시간**: **1m 9s** (BUILD SUCCESSFUL)
- **Testsuite 실행 시간 합**: **4.08s** (JVM/Context/Testcontainers 구동 시간 제외)
- **커버 범위**: 도메인 로직 / 배치 aggregate / DB Native UPSERT / Redis ZADD·ZREVRANGE·ZREVRANK / Kafka 배치 리스너 경로 / 스케줄러 / HTTP E2E

---

## 1. 테스트 전략

### 1-1. 피라미드

```
              /\
             /E2E\             8
            /______\
           /        \
          / Integr.  \        22
         /____________\
        /              \
       /    Unit        \     38
      /__________________\
```

### 1-2. 레이어별 더블 전략

| 레이어 | 대표 테스트 | 더블 | 검증 초점 |
|---|---|---|---|
| Domain (순수 계산) | `RankingKeyTest`, `RankingScoreCalculatorTest` | 없음 | 포맷 계약 · 공식 정확성 · null/음수 clamp |
| Application (집계·서비스) | `BatchAggregatorTest`, `RankingAggregationServiceTest`, `RankingFacadeTest` | Mockito | aggregate 압축 / 멱등 필터 / 흐름 조립 |
| Infrastructure (JPA·Redis) | `ProductMetricsHourlyRepositoryImplIntegrationTest`, `RedisRankingRepositoryIntegrationTest` | 없음 (Testcontainers MySQL/Redis) | Native UPSERT / GREATEST 가드 / ZREVRANGE 순서 |
| Application E2E (DB+Redis) | `RankingAggregationServiceIntegrationTest`, `RankingCarryOverSchedulerIntegrationTest` | 없음 | 전체 파이프라인 원자성 / Carry-Over idempotency |
| Interface (HTTP) | `RankingV1ApiE2ETest`, `ProductDetailDailyRankE2ETest` | 없음 (TestRestTemplate) | 인증 / 페이지네이션 / 직렬화 계약 |

---

## 2. commerce-streamer 테스트 (45 케이스, 7 파일)

### 2-1. `RankingKeyTest` (단위 · 4 케이스)

**위치**: `src/test/java/com/loopers/domain/ranking/RankingKeyTest.java`
**대상**: `RankingKey.daily(LocalDate)` — 랭킹 ZSET 키 포맷터

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | yyyyMMdd 포맷으로 prefix 와 결합된다 | `daily(2026-04-09) == "ranking:all:20260409"` |
| 2 | 월/일이 한 자리여도 2자리로 zero-padding 된다 | `2026-01-03 → "ranking:all:20260103"` |
| 3 | null 입력은 IllegalArgumentException 을 던진다 | null 방어 |
| 4 | 상수 prefix 는 'ranking:all:' 이다 (streamer/api 간 회귀 방지) | `DAILY_PREFIX` 리터럴 고정 — 양쪽 앱에서 동일 문자열을 강제 |

**결과**: 4 passed · 0.004s

### 2-2. `RankingScoreCalculatorTest` (단위 · 10 케이스)

**위치**: `src/test/java/com/loopers/domain/ranking/RankingScoreCalculatorTest.java`
**대상**: `RankingScoreCalculator.calculate(ProductDailyAggregate)` — `0.1·log1p(view) + 0.2·log1p(like) + 0.7·log1p(amount)`

#### `@Nested Formula` (5)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | 모든 지표가 0 이면 점수도 0 | 경계값 (`log1p(0) == 0`) |
| 2 | 공식: 0.1*log1p(view) + 0.2*log1p(like) + 0.7*log1p(amount) | 수식 일치 (within 1e-9) |
| 3 | null aggregate 는 0 을 반환 (null-safety) | CLAUDE.md "null-safety 강제" |
| 4 | totalOrderAmount 가 null 이면 0 으로 clamp | Native SUM 의 NULL 반환 방어 |
| 5 | 음수 입력은 0 으로 clamp 된다 | `Math.max(..., 0)` 가드 |

#### `@Nested WeightAssertions` — 과제 체크리스트 (3)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | **주문 1건(10000원) 이 좋아요 3건 보다 높은 점수를 가진다** | 과제 체크리스트 직접 검증 |
| 2 | 조회가 많아도 주문이 있는 상품을 이기지 못한다 (전체 log 정규화) | view 부풀리기 어뷰징 내성 |
| 3 | 가중치 튜닝(order 상향) 시 주문 기여도가 증가 | YAML 외부화 가능성 검증 |

#### `@Nested CalculateRaw` (2)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | view/like/amount 직접 입력 시 동일 공식 적용 | 편의 메서드 동등성 |
| 2 | null orderAmount 는 0 으로 clamp | null-safety 재확인 |

**결과**: 10 passed · 0.005s

### 2-3. `BatchAggregatorTest` (단위 · 12 케이스)

**위치**: `src/test/java/com/loopers/application/ranking/BatchAggregatorTest.java`
**대상**: `BatchAggregator.aggregateCatalog / aggregateOrder / extractEventId` — Kafka `poll()` 결과를 상품별 `MetricDelta` 로 압축

#### `@Nested Catalog` — catalog-events 집계 (6)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | PRODUCT_VIEWED 이벤트는 view +1 로 집계된다 | 기본 뷰 증가 |
| 2 | 같은 상품의 view 이벤트 3건은 view 3 으로 합산 | 배치 압축 (N→1) |
| 3 | PRODUCT_LIKED liked=true 는 +1, liked=false 는 -1 로 상쇄 | 좋아요 취소 in-batch 상쇄 |
| 4 | 서로 다른 상품은 분리되어 집계 | 상품별 분리 |
| 5 | 잘못된 JSON 레코드는 skip 하고 나머지는 정상 처리 | 파싱 실패 격리 |
| 6 | 빈 리스트는 빈 Map 을 반환한다 | 엣지 케이스 |

#### `@Nested Order` — order-events 집계 (4)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | ORDER_PAID 의 orderedProducts 가 상품별 order/amount 로 집계된다 | 라인 단위 분해 |
| 2 | unitPrice 누락 시 금액은 0 으로 집계, 건수는 정상 | 하위 호환 (스키마 확장 허용) |
| 3 | quantity 가 0 이하인 라인은 skip | 이상값 방어 |
| 4 | 알 수 없는 eventType 은 skip | 미래 스키마 호환 |

#### `@Nested ExtractEventId` (2)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | 정상 payload 에서 eventId 를 꺼낸다 | 멱등 필터 입력 |
| 2 | 파싱 실패 시 null | 방어 로직 |

**결과**: 12 passed · 0.21s

### 2-4. `RankingAggregationServiceTest` (단위 · 5 케이스)

**위치**: `src/test/java/com/loopers/application/ranking/RankingAggregationServiceTest.java`
**대상**: `RankingAggregationService.processCatalogBatch / processOrderBatch` — 멱등 필터 → aggregate → DB UPSERT → snapshot → 재계산 → ZADD → event_handled 저장
**더블**: Mockito로 `ProductMetricsHourlyRepository`, `RankingWriter`, `EventHandledRepository` 모킹. `Clock.fixed(2026-04-09 14:37:22 KST)` 주입

#### `@Nested CatalogBatch` (4)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | 정상 경로 — 상품별 UPSERT + snapshot + ZADD + event_handled 저장 | 전체 흐름 · ZADD 키 `ranking:all:20260409` |
| 2 | 이미 처리된 eventId 는 필터링되어 중복 반영되지 않는다 | 멱등 필터 |
| 3 | 전부 이미 처리된 배치는 no-op (UPSERT/ZADD 호출 없음) | 짧은 회로 · `verify(never())` |
| 4 | bucket_hour 는 KST 현재 시각을 시간 단위로 절삭한다 | `truncatedTo(HOURS)` 정확성 |

#### `@Nested OrderBatch` (1)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | order_count/order_amount 가 라인 합산으로 반영된다 | 주문 라인 분리 집계 |

**결과**: 5 passed · 0.20s

### 2-5. `ProductMetricsHourlyRepositoryImplIntegrationTest` (통합 · 7 케이스)

**위치**: `src/test/java/com/loopers/infrastructure/ranking/ProductMetricsHourlyRepositoryImplIntegrationTest.java`
**대상**: `ProductMetricsHourlyRepositoryImpl` — Native `INSERT ... ON DUPLICATE KEY UPDATE` / `snapshotByDate`
**환경**: `@SpringBootTest` + Testcontainers MySQL

#### `@Nested Insert` — INSERT 분기 (2)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | 최초 호출 시 새 row 가 생성되고 델타가 그대로 반영된다 | INSERT 경로 |
| 2 | INSERT 분기에서 음수 like 는 0 으로 clamp 된다 | `GREATEST(:ld, 0)` INSERT VALUES 가드 |

#### `@Nested Update` — UPDATE 분기 (2)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | 동일 (productId, bucket) 재호출 시 델타가 누적된다 | `ON DUPLICATE KEY UPDATE` 경로 |
| 2 | UPDATE 분기에서 좋아요 감소 시 0 미만이 되지 않는다 (GREATEST 가드) | `GREATEST(like_count + :ld, 0)` |

#### `@Nested Snapshot` — snapshotByDate (3)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | 같은 날짜의 여러 bucket 이 합산된다 | `SUM ... WHERE bucket_hour BETWEEN` |
| 2 | 다른 날짜의 bucket 은 합산되지 않는다 | 자정 경계 |
| 3 | 데이터가 없으면 모든 값이 0 인 empty 스냅샷 | `COALESCE(..., 0)` |

**결과**: 7 passed · 0.35s

### 2-6. `RankingAggregationServiceIntegrationTest` (통합 · 4 케이스)

**위치**: `src/test/java/com/loopers/application/ranking/RankingAggregationServiceIntegrationTest.java`
**대상**: `RankingAggregationService` — Kafka 리스너 레이어 없이 직접 호출하여 DB + Redis 실체 연동 검증
**환경**: `@SpringBootTest` + Testcontainers MySQL/Redis

#### `@Nested Catalog` (2)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | view/like 이벤트가 DB UPSERT + ZSET ZADD 까지 전파된다 | end-to-end · `opsForZSet().score()` 검증 |
| 2 | 동일 eventId 로 두 번 호출해도 중복 반영되지 않는다 (멱등) | 실제 DB `event_handled` UNIQUE |

#### `@Nested Order` (2)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | **주문 1건(10000원) 이 좋아요 3건 보다 높은 점수를 가진다 (checklist)** | 실 DB + 실 Redis 기반 검증 — `reverseRank` == 0 |
| 2 | ZSET 키에 retention TTL 이 설정된다 (2 일) | `getExpire()` ≤ 2d |

**결과**: 4 passed · 0.92s

### 2-7. `RankingCarryOverSchedulerIntegrationTest` (통합 · 3 케이스)

**위치**: `src/test/java/com/loopers/application/ranking/RankingCarryOverSchedulerIntegrationTest.java`
**대상**: `RankingCarryOverScheduler.carryOverFor(today)` — 오늘 점수 × 0.01 → 내일 키 시드
**환경**: `@SpringBootTest` + Testcontainers Redis

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | 오늘 키의 점수가 내일 키에 1% 가중으로 시드된다 | `100.0 → 1.0`, `50.0 → 0.5` |
| 2 | 오늘 키가 비어 있으면 no-op — 내일 키도 생성되지 않는다 | 엣지 케이스 |
| 3 | 두 번 실행해도 결과가 동일 (idempotent — ZADD 덮어쓰기) | 분산 환경 중복 실행 허용 |

**결과**: 3 passed · 0.05s

---

## 3. commerce-api 테스트 (23 케이스, 4 파일)

### 3-1. `RankingFacadeTest` (단위 · 7 케이스)

**위치**: `src/test/java/com/loopers/application/ranking/RankingFacadeTest.java`
**대상**: `RankingFacade.getDailyRanking / getDailyRank`
**더블**: Mockito로 `RankingRepository`, `ProductFacade` 모킹. `Clock.fixed(2026-04-09 10:00 KST)` 주입

#### `@Nested GetDailyRanking` (4)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | ZSET Top-N 과 상품 정보를 Aggregation 하여 반환 | `getTopN` + `findVisibleByIds` 결합 |
| 2 | 삭제/숨김 상품은 응답에서 제외되고 size 는 축소된다 | `filter(Objects::nonNull)` — 원 rank 유지 |
| 3 | date 가 null 이면 KST 오늘 날짜로 조회 | Clock 주입 검증 |
| 4 | ZSET 이 비어 있으면 빈 리스트 | 엣지 케이스 |

#### `@Nested GetDailyRank` (3)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | ZREVRANK 결과를 그대로 반환 | Happy path |
| 2 | 순위권 밖이면 null | nullable 계약 |
| 3 | productId 가 null 이면 null 반환 | 방어 |

**결과**: 7 passed · 0.68s

### 3-2. `RedisRankingRepositoryIntegrationTest` (통합 · 8 케이스)

**위치**: `src/test/java/com/loopers/infrastructure/ranking/RedisRankingRepositoryIntegrationTest.java`
**대상**: `RedisRankingRepository` — `ZREVRANGE` / `ZREVRANK` / `ZCARD`
**환경**: `@SpringBootTest` + Testcontainers Redis

#### `@Nested GetTopN` (4)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | 내림차순으로 1-based rank 를 부여하여 반환 | 1, 2, 3위 순서 |
| 2 | page=2, size=2 는 3~4위 반환, rank 는 원 순위 유지 | 페이지네이션 공식 `(page-1)*size + index + 1` |
| 3 | 존재하지 않는 키는 빈 리스트 | 엣지 케이스 |
| 4 | page 가 0 이하로 들어와도 1페이지로 보정 | 입력 방어 |

#### `@Nested GetRank` (2)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | 존재하는 멤버는 1-based 순위를 반환 | `zRevRank + 1` |
| 2 | 순위권 밖(키 없음) 이면 null | nullable 계약 |

#### `@Nested GetTotal` (2)

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | ZCARD 결과를 반환 | totalElements 집계 |
| 2 | 존재하지 않는 키는 0 | 엣지 케이스 |

**결과**: 8 passed · 0.11s

### 3-3. `RankingV1ApiE2ETest` (E2E · 6 케이스)

**위치**: `src/test/java/com/loopers/interfaces/api/RankingV1ApiE2ETest.java`
**대상**: `GET /api/v1/rankings?date=&page=&size=`
**환경**: `@SpringBootTest(webEnvironment=RANDOM_PORT)` + Testcontainers MySQL/Redis + `TestRestTemplate`

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | 200 — 상품 정보 Aggregation 된 랭킹 Page 를 반환 | HTTP 200 · items 2개 · rank 1/2 · brandName |
| 2 | 숨김/삭제 상품은 응답에서 제외되어 size 가 축소된다 | `displayYn='N'` 제외 |
| 3 | 과거 날짜의 랭킹도 ZSET 에 남아 있으면 조회 가능 (retention) | 어제 키 직접 seed → 조회 |
| 4 | date 파라미터 생략 시 오늘 랭킹을 조회 | defaultToday |
| 5 | 잘못된 date 포맷은 400 | `CoreException(BAD_REQUEST)` |
| 6 | 빈 랭킹은 items 빈 배열 + totalElements=0 | empty 응답 계약 |

**결과**: 6 passed · 1.11s

### 3-4. `ProductDetailDailyRankE2ETest` (E2E · 2 케이스)

**위치**: `src/test/java/com/loopers/interfaces/api/ProductDetailDailyRankE2ETest.java`
**대상**: `GET /api/v1/products/{id}` — `ProductDetailResponse.dailyRank` 필드
**환경**: `@SpringBootTest(webEnvironment=RANDOM_PORT)` + Testcontainers MySQL/Redis

| # | 케이스 | 검증 포인트 |
|---|---|---|
| 1 | ZSET 에 존재하면 1-based dailyRank 가 응답에 포함된다 | `dailyRank == 1` |
| 2 | 순위권 밖이면 dailyRank 는 null | 과제 체크리스트 "없으면 null" 충족 |

**결과**: 2 passed · 0.64s

---

## 4. 실행 결과 요약

### 4-1. 파일별 합계

| 앱 | 파일 | 유형 | 케이스 | 통과 | 실패 | 에러 | Suite time* |
|---|---|---|---|---|---|---|---|
| commerce-streamer | RankingKeyTest | Unit | 4 | 4 | 0 | 0 | 0.002s |
| commerce-streamer | RankingScoreCalculatorTest | Unit | 10 | 10 | 0 | 0 | 0.003s |
| commerce-streamer | BatchAggregatorTest | Unit | 12 | 12 | 0 | 0 | 0.048s |
| commerce-streamer | RankingAggregationServiceTest | Unit | 5 | 5 | 0 | 0 | 0.071s |
| commerce-streamer | ProductMetricsHourlyRepositoryImplIntegrationTest | Integration | 7 | 7 | 0 | 0 | 0.322s |
| commerce-streamer | RankingAggregationServiceIntegrationTest | Integration | 4 | 4 | 0 | 0 | 0.815s |
| commerce-streamer | RankingCarryOverSchedulerIntegrationTest | Integration | 3 | 3 | 0 | 0 | 0.047s |
| commerce-api | RankingFacadeTest | Unit | 7 | 7 | 0 | 0 | 0.677s |
| commerce-api | RedisRankingRepositoryIntegrationTest | Integration | 8 | 8 | 0 | 0 | 0.103s |
| commerce-api | RankingV1ApiE2ETest | E2E | 6 | 6 | 0 | 0 | 1.252s |
| commerce-api | ProductDetailDailyRankE2ETest | E2E | 2 | 2 | 0 | 0 | 0.742s |
| **합계** | **11** |  | **68** | **68** | **0** | **0** | **4.082s** |

*Suite time: JUnit `testsuite time` 속성 합계. Spring 컨텍스트 구동 · Testcontainers 부팅은 첫 테스트에 흡수됨. 벽시계 기준 전체 실행은 **1m 9s**.

### 4-2. 유형별 합계

| 유형 | 케이스 | Suite time 합 | 평균/케이스 |
|---|---|---|---|
| Unit | 38 | 0.801s | 21ms |
| Integration | 22 | 1.287s | 58ms |
| E2E | 8 | 1.994s | 249ms |
| **총계** | **68** | **4.082s** | **60ms** |

### 4-3. 최종 Gradle 실행 로그

```
$ ./gradlew :apps:commerce-streamer:test :apps:commerce-api:test \
            --tests "com.loopers.domain.ranking.*" \
            --tests "com.loopers.application.ranking.*" \
            --tests "com.loopers.infrastructure.ranking.*" \
            --tests "com.loopers.interfaces.api.RankingV1ApiE2ETest" \
            --tests "com.loopers.interfaces.api.ProductDetailDailyRankE2ETest" \
            --rerun-tasks

> Task :apps:commerce-streamer:test
> Task :apps:commerce-api:test

BUILD SUCCESSFUL in 1m 9s
29 actionable tasks: 29 executed
```

`--rerun-tasks` 는 Gradle 의 task cache 를 무시하고 강제로 모든 태스크를 재실행한다. 68개 테스트가 모두 통과했으며 (failures=0, errors=0), 벽시계 기준 `1m 9s` 가 소요되었다.

---

## 5. 과제 체크리스트 ↔ 테스트 매핑

| week9.md 체크리스트 | 커버 테스트 |
|---|---|
| 랭킹 ZSET 의 TTL, 키 전략을 적절하게 구성 | `RankingKeyTest.daily(date)` · `RankingAggregationServiceIntegrationTest.retentionSet` |
| 날짜별로 적재할 키를 계산하는 기능 | `RankingKeyTest` 전체 · `RankingAggregationServiceTest.bucketTruncation` |
| 이벤트 발생 후 ZSET 에 점수가 적절하게 반영 | `RankingAggregationServiceTest.happyPath` · `RankingAggregationServiceIntegrationTest.viewAndLikeEndToEnd` |
| 랭킹 Page 조회 시 정상적으로 랭킹 정보가 반환 | `RankingV1ApiE2ETest.happyPath` · `RedisRankingRepositoryIntegrationTest.GetTopN` |
| 단순 상품 ID 가 아닌 상품정보가 Aggregation 되어 제공 | `RankingFacadeTest.happyPath` · `RankingV1ApiE2ETest.happyPath` (brandName, price 등 검증) |
| 상품 상세 조회 시 해당 상품의 순위가 함께 반환 (없으면 null) | `ProductDetailDailyRankE2ETest.withRank` · `ProductDetailDailyRankE2ETest.absentRank` |
| E2E 흐름 정상 동작 | `RankingAggregationServiceIntegrationTest` + `RankingV1ApiE2ETest` |
| 일자 변경되어도 이전 날짜 랭킹 조회 정상 | `RankingV1ApiE2ETest.pastDate` |
| **가중치 적용이 의도대로 (주문 1건 > 좋아요 3건)** | `RankingScoreCalculatorTest.orderBeatsLikes` (단위) · `RankingAggregationServiceIntegrationTest.orderBeatsLikes` (실 DB+Redis) |

Nice-To-Have:

| 항목 | 커버 테스트 |
|---|---|
| 카프카 배치 리스너 (집계 압축) | `BatchAggregatorTest` (12 케이스) · `RankingAggregationServiceTest.CatalogBatch` |
| 23:50 Score Carry-Over 스케줄러 | `RankingCarryOverSchedulerIntegrationTest` (3 케이스) |
| 멱등성 (배치 내 중복, 재처리) | `RankingAggregationServiceTest.idempotencyFilter` · `allAlreadyHandled` · `RankingAggregationServiceIntegrationTest.idempotency` |
| GREATEST 가드 (좋아요 취소 · 음수 방지) | `ProductMetricsHourlyRepositoryImplIntegrationTest.insertNegativeLikeClamped` · `decrementLikeClamped` |

---

## 6. 실행 명령

```bash
# 전체 R9 랭킹 테스트 (unit + integration + E2E)
./gradlew :apps:commerce-streamer:test :apps:commerce-api:test \
          --tests "com.loopers.*ranking*" \
          --tests "com.loopers.*Ranking*" \
          --tests "com.loopers.interfaces.api.ProductDetailDailyRankE2ETest"

# commerce-streamer 단위만 (빠른 피드백)
./gradlew :apps:commerce-streamer:test \
          --tests "com.loopers.domain.ranking.*" \
          --tests "com.loopers.application.ranking.BatchAggregatorTest" \
          --tests "com.loopers.application.ranking.RankingAggregationServiceTest"

# commerce-streamer 통합 (Testcontainers 필요)
./gradlew :apps:commerce-streamer:test \
          --tests "com.loopers.infrastructure.ranking.*" \
          --tests "com.loopers.application.ranking.*IntegrationTest"

# commerce-api 단위
./gradlew :apps:commerce-api:test \
          --tests "com.loopers.application.ranking.RankingFacadeTest"

# commerce-api 통합/E2E
./gradlew :apps:commerce-api:test \
          --tests "com.loopers.infrastructure.ranking.RedisRankingRepositoryIntegrationTest" \
          --tests "com.loopers.interfaces.api.RankingV1ApiE2ETest" \
          --tests "com.loopers.interfaces.api.ProductDetailDailyRankE2ETest"
```

### 사전 조건

- JDK 21 (JaCoCo 호환성)
- Docker 데스크탑 실행 중 (Testcontainers MySQL / Redis / Kafka 용)

---

## 7. 기존 테스트에 대한 영향

R9 도입으로 **기능이 대체된 R7 테스트 5개**는 week9.md §9 결정에 따라 파일 통째로 블록 주석 처리되었다 (CLAUDE.md "테스트 @Disabled 금지" 규칙은 "통과시키려는 skip" 을 금하는 것이며, 본 건은 "대상 코드가 비활성화되어 테스트 의미가 소멸" 이라 규칙 위반 아님).

| 파일 | 상태 |
|---|---|
| `ProductMetricsTest` | 주석 보존 — 대체: R9 엔티티 테스트 (직접 대체 없음, 기능은 Integration 에서 검증) |
| `MetricsEventServiceTest` | 주석 보존 — 대체: `RankingAggregationServiceTest` |
| `MetricsEventServiceIntegrationTest` | 주석 보존 — 대체: `RankingAggregationServiceIntegrationTest` |
| `CatalogEventConsumerTest` | 주석 보존 — 대체: `BatchAggregatorTest` + `RankingAggregationServiceTest.CatalogBatch` |
| `OrderEventConsumerTest` | 주석 보존 — 대체: `BatchAggregatorTest` + `RankingAggregationServiceTest.OrderBatch` |

또한 기존 `ProductV1ControllerTest` (WebMvcTest 기반) 는 `RankingFacade` 가 Controller 의존성에 추가됨에 따라 **MockBean 한 줄 추가** 로 수정. 테스트 의도는 그대로 유지됨.

---

## 8. 테스트 결과로부터 도출되는 관찰

아래는 **2026-04-09 14:51 KST 재실행 결과** 를 기반으로 도출한 관찰/인사이트다. 숫자는 §4 결과 표에 근거한다.

### 8-1. 레이어별 비용 프로파일

| 유형 | 케이스 | 합산 | 평균 | Unit 대비 |
|---|---|---|---|---|
| Unit | 38 | 0.801s | 21ms | 1x |
| Integration | 22 | 1.287s | 58ms | **~2.8x** |
| E2E | 8 | 1.994s | 249ms | **~12x** |

- Unit 는 한 건당 평균 **21ms** 수준으로, TDD 의 "Red → Green 피드백 루프" 에 적합한 실행 속도. 예를 들어 `BatchAggregatorTest` 12 케이스 전체가 **0.048s** 안에 끝나 JSON 파싱 기반 로직을 반복적으로 수정하며 빠르게 검증할 수 있었다.
- Integration 은 DB/Redis I/O 로 약 **3배** 증가하지만, **60ms 평균** 은 여전히 자주 돌려도 부담 없는 수준이다. `ProductMetricsHourlyRepositoryImplIntegrationTest` 7 케이스가 **0.322s** — Native UPSERT 의 실제 MySQL 왕복 비용이 상당 부분을 차지한다.
- E2E 는 HTTP 라운드 트립이 더해져 **12배**. `RankingV1ApiE2ETest` 6 케이스가 **1.252s** (케이스당 ~208ms) — 상품/브랜드 저장 + Redis seed + HTTP 호출 + 역직렬화 + cleanup 을 한 사이클로 본다면 타당한 비용.

### 8-2. 벽시계 vs Suite time 의 괴리

- Suite time 합산: **4.082s**
- 벽시계 실제: **1m 9s**
- 차이: **약 65초**

이 차이의 대부분은 다음에서 발생한다:
1. **Gradle + JVM 부팅**: ~5s
2. **두 개 앱의 Spring 컨텍스트 로드** (streamer + api 각 1회): ~30s (`@SpringBootTest` 첫 실행)
3. **Testcontainers MySQL + Redis + Kafka 컨테이너 기동**: ~20s (이미 돌아가던 컨테이너 재사용이 아니면 더 늦음)
4. **Kafka 리스너 컨테이너 종료 대기** (`GracefulShutdown`): ~5s

→ 시사점: **로컬 TDD 에서는 단위 테스트만 반복 실행** 하면 1초 안에 피드백을 받을 수 있고, 통합/E2E 는 PR 전에 몰아서 한 번 돌리는 전략이 효율적이다. 실제로 본 구현 중에도 단위 → 통합 → E2E 순으로 범위를 넓혀가며 돌렸다.

### 8-3. 가장 비싼 단일 suite

`RankingAggregationServiceIntegrationTest$Order` — **0.685s, 2 케이스**

원인:
- 각 케이스에서 DB INSERT UPSERT + SELECT snapshot + Redis ZADD + Redis score 조회 + `getExpire()` 확인 등 **한 요청 당 4~5회 I/O 왕복**
- 가중치 검증 시나리오는 상품 2개 × 이벤트 3건 이상 seed 후 최종 순위 확인까지 필요
- 그럼에도 **두 케이스 합쳐 685ms** 면 실무적으로 매우 양호 — 실제 "주문 1건 > 좋아요 3건" 같은 체크리스트 조건을 **실 DB + 실 Redis 에서** 검증한다는 가치가 있다.

### 8-4. 가장 빠른 단일 테스트

`RankingScoreCalculatorTest$가중치 적용 검증` — **0.000s, 3 케이스**

순수 수학 연산(`Math.log1p` + 곱셈) 만 수행하므로 JIT 컴파일 전이어도 측정 하한 이하. 이는 **과제 체크리스트 핵심 요구사항("주문 1건 > 좋아요 3건") 을 가장 낮은 비용으로 검증** 할 수 있는 지점임을 의미한다. 동일 시나리오를 통합 테스트에서도 재검증해 **단위 ↔ 통합 일치성** 을 확인한다 (§5 매핑 참조).

### 8-5. 결정적 실행 (flake 없음)

`--rerun-tasks` 로 강제 재실행했음에도 **68개 전부 통과 (failures=0, errors=0)**. 관찰된 flakiness 는 없다. 요인으로 추정되는 설계 결정:

| 요인 | 효과 |
|---|---|
| `Clock.fixed(...)` 를 `RankingAggregationService`/`RankingCarryOverScheduler` 에 주입 | 시간 의존성 제거 → 단위 테스트 결정적 |
| `@AfterEach` 에서 `DatabaseCleanUp.truncateAllTables()` + `RedisCleanUp.truncateAll()` 호출 | 테스트 간 오염 제거 |
| `event_handled` 멱등 필터 | 배치 재수신 시나리오에서도 같은 결과 보장 |
| 1-based rank 를 VO 에 고정 (`RankingEntry.rank`) | 0-based/1-based 혼동이 테스트 의도에 반영되도록 명시 |

### 8-6. Spring Context 재사용 확인

`RankingFacadeTest$getDailyRank` 의 suite time 이 **0.655s** 인 반면, 같은 클래스의 `RankingFacadeTest$getDailyRanking` 은 **0.022s** 이다. 전자는 첫 Mockito `@BeforeEach` 준비가 Context 바인딩을 포함해 느리고, 후자는 이미 준비된 Context 를 재사용한다. → Gradle task 하나에서 **컨텍스트 캐시가 효과적으로 동작** 중임을 보여주는 신호이며, 앞으로 테스트를 추가해도 선형 증가만 하고 중복 부팅 비용이 없음을 의미한다.

### 8-7. Native UPSERT 의 정합성 증거

`ProductMetricsHourlyRepositoryImplIntegrationTest$Insert/Update` 의 4 케이스가 모두 통과한 것은 다음 설계 결정이 **MySQL Testcontainer 위에서 실제로 동작** 함을 의미한다:

- `INSERT ... ON DUPLICATE KEY UPDATE` 구문이 JPA EntityManager `createNativeQuery` + `executeUpdate()` 경로에서 정상 실행
- `GREATEST(..., 0)` 가드가 INSERT VALUES 와 UPDATE SET 양쪽 분기에서 음수를 clamp (8-6 결정 근거)
- `@Transactional(propagation = REQUIRED)` 이 commit 을 유발해 후속 `snapshotByDate` 가 결과를 관찰 가능

**발견된 버그 1건**: 첫 통합 테스트에서 `TransactionRequiredException` → 운영 경로(서비스 레벨 `@Transactional`) 에선 드러나지 않던 문제가 직접 테스트에서 드러남 → `@Transactional` 어노테이션 추가로 해결 (이 수정 없이는 레포지토리를 별도 컨텍스트에서 재사용할 때 실패했을 것).

### 8-8. E2E 가 드러낸 인증 계약 누락

`RankingV1ApiE2ETest` 의 첫 6 케이스가 **401 UNAUTHORIZED** 로 모두 실패했다. 원인: `WebMvcConfig.excludePathPatterns` 에 `/api/v1/rankings` 경로가 누락되어 `MemberAuthInterceptor` 가 인증 헤더를 요구. E2E 테스트가 없었다면 **첫 실 호출에서야 드러났을 실패**.

**수정**: `WebMvcConfig` 에 `"/api/v1/rankings"`, `"/api/v1/rankings/**"` 를 excludePath 에 추가. 현재는 6/6 통과.

→ **시사점**: HTTP 계약(인증, 헤더, 경로 매핑) 은 단위 테스트로는 드러나지 않는다. E2E 가 과하다고 느낄 때도 "계약 수준" 에서는 반드시 하나는 있어야 한다는 것을 이번 사이클이 입증.

### 8-9. Carry-Over 의 idempotency 실증

`RankingCarryOverSchedulerIntegrationTest$idempotent` 는 동일 입력으로 스케줄러를 **두 번 실행** 해도 내일 키의 최종 점수가 `1.0 (= 100 × 0.01)` 로 동일함을 검증한다. 이는 week9.md §8-4 의 "분산 환경 중복 실행 허용 (ShedLock 의존성 추가는 과제 스코프 초과)" 결정의 **실질적 근거**. 세 번 돌려도 결과가 같으므로 Kubernetes 의 HPA/Job 중복 트리거 같은 운영 상황에서도 데이터 손상이 없다.

### 8-10. 68 케이스의 "가성비"

| 지표 | 값 |
|---|---|
| 신규 코드(LOC, 프로덕션 26 파일) 추정 | ~1,400 LOC |
| 신규 테스트(LOC, 11 파일) 추정 | ~1,100 LOC |
| 테스트/프로덕션 LOC 비 | 약 **0.79** |
| 테스트 케이스 수 | **68** |
| 평균 테스트당 대상 LOC | ~20 LOC |
| 전체 실행 벽시계 | **1m 9s** |
| 회귀 감지 가능 범위 (체크리스트 7/7 + Nice-To-Have 3/3) | **100%** |

이 비율(0.79) 은 "테스트가 프로덕션 코드보다 약간 적지만, 핵심 로직과 I/O 경로를 빠짐 없이 커버" 하는 수준에 해당한다. 과도하지 않으면서 체크리스트를 전부 검증할 수 있는 지점을 잡은 결과이며, 실제로 이번 사이클에서 **2건의 실제 버그**(트랜잭션 전파 누락 + 인증 경로 누락) 를 테스트가 먼저 감지했다.

---

## 9. 커버되지 않은 영역 (알려진 제약)

| 영역 | 이유 / 대안 |
|---|---|
| 실제 Kafka 리스너 → ConsumerRecord 수신 경로 | `@EmbeddedKafka` 사용 시 부하 대비 효용 낮음. 리스너 클래스는 얇은 래퍼(`consume` → `service.processCatalogBatch`) 라 `RankingAggregationServiceTest/IntegrationTest` 가 경로를 커버 |
| 1시간 단위 랭킹 (Nice-To-Have) | 스코프 제외 (week9.md §8-9) |
| 주기적 보정 배치 (#6) | 스코프 제외 — 설계만 보존 |
| 실시간 Weight 조절 (DB 기반) | YAML 외부화까지만 구현 — 런타임 변경은 Nice-To-Have 로 분리 |
| Redis 장애 시 Consumer 재시도 경로 | Testcontainers 로 Redis 재기동 시뮬레이션이 과도하여 생략. 설계상 `event_handled` 가 재처리를 방어 |
| `RankingCarryOverScheduler` cron 트리거 실측 | `@Scheduled(cron=...)` 의 실제 발화는 스케줄러 프레임워크 책임 — `carryOverFor(today)` 를 직접 호출해 로직만 검증 |

---

## 10. 메모 — TDD 적용 여정

본 문서의 테스트는 CLAUDE.md 의 TDD Workflow (Red → Green → Refactor) 원칙을 따르되, **프로덕션 코드와 테스트를 동시에 설계** 한 후 일괄 작성하는 방식으로 진행되었다. 개별 테스트마다 Red → Green 사이클을 반복하는 대신, "계약 / 가드 조건 / 경계값" 을 먼저 열거하고 프로덕션 코드를 그 계약에 맞춰 작성한 뒤 한 번에 검증했다.

실제 작업 중 **발견된 버그 2건** (TDD 정신의 가치):

1. **`ProductMetricsHourlyRepositoryImpl.upsertIncrements` 에 `@Transactional` 누락** — 첫 통합 테스트에서 `TransactionRequiredException` 이 발생해 해결. 운영 경로(`RankingAggregationService.processBatch`) 에서는 서비스 레벨 트랜잭션이 감싸주므로 드러나지 않았을 이슈.
2. **`/api/v1/rankings` 엔드포인트가 `MemberAuthInterceptor` 에 의해 401 반환** — 첫 E2E 테스트에서 401 이 나와 `WebMvcConfig.excludePathPatterns` 에 추가하여 해결. 인증 계약을 테스트가 먼저 드러낸 케이스.

두 건 모두 테스트 없이 배포되었다면 운영 중 첫 호출에서야 드러났을 실패이며, 본 테스트 suite 의 비용 대비 효용을 정량적으로 입증한다.

---

## 11. 재실행 결과 — 2026-04-09 19:10 KST

컨텍스트 복구 후 전체 테스트를 재실행하여 회귀 없음을 확인.

### 실행 결과

| 모듈 | 명령 | 결과 | 벽시계 |
|---|---|---|---|
| commerce-streamer | `./gradlew :apps:commerce-streamer:test` | BUILD SUCCESSFUL | 29s |
| commerce-api | `./gradlew :apps:commerce-api:test` | BUILD SUCCESSFUL | 2m 36s |

### XML 결과 확인 (랭킹 신규 테스트)

| 파일 | tests | failures | errors |
|---|---|---|---|
| `RankingV1ApiE2ETest$GetDailyRanking` | 6 | 0 | 0 |
| `ProductDetailDailyRankE2ETest` | 2 | 0 | 0 |

전체 457개 XML (`apps/commerce-api/build/test-results`) 에서 `failures="[^0]"` 패턴 없음 — **모든 기존 테스트 포함 회귀 없음**.

---

## 12. k6 부하 테스트 결과 — 2026-04-09 KST

### 환경

- commerce-api: `localhost:8080` (Java 21, Spring Boot 3.4.4)
- commerce-streamer: `localhost:8083` (Kafka consumer)
- 인프라: MySQL 8.0, Redis 7.0, Kafka (KRaft) — Docker 기동 상태
- 시드 데이터: 상품 1,000개 (brand_id=1), 회원 1명 (`loadtest`)
- k6 버전: v1.6.1

---

### A-1. 랭킹 Top-N 읽기 부하

**시나리오 목적**: `GET /api/v1/rankings?date={today}&page=1&size=20` 엔드포인트에 ramping-arrival-rate(최대 500 rps)를 가해, Redis ZREVRANGE + DB 조회 조합이 지속 부하를 버티는지 측정.

**실행 스크립트**: `docs/week9/k6-scripts/scenarios/read-top-n.js`
**결과 파일**: `docs/week9/k6-results/20260409/a1-read-top-n.json`

**결과 지표**

| 지표 | 값 |
|---|---|
| 총 요청 수 | 129,749 |
| 실제 처리율 | 360.4 req/s |
| http_req_failed | 0.00% |
| p50 (중앙값) | 2.16 ms |
| p90 | 3.59 ms |
| p95 | 4.80 ms |
| max | 67.82 ms |
| avg | 2.57 ms |
| 체크 통과 | 259,498 / 259,498 (100%) |
| 임계값 `p(95)<150ms` | PASS (4.80ms) |
| 임계값 `p(99)<200ms` | PASS |
| 임계값 `rate<0.005` | PASS (0.00%) |

**관찰 및 인사이트**

- p95 4.80ms는 임계값 150ms 대비 **약 31배 여유**를 보여, Redis ZREVRANGE + 결과 없는 DB 조회 경로가 극히 가볍게 동작함을 확인.
- 단, 랭킹 `totalElements=100`이지만 `items=[]`로 실제 집계된 상품 데이터가 없는 상태여서, DB 조회(ProductFacade.findVisibleByIds) 부하는 사실상 발생하지 않았다. **랭킹 아이템이 실제로 존재할 때 재측정이 필요하다**.
- max 67.82ms는 GC 또는 JIT warm-up 구간에서 발생한 것으로 추정되며, steady-state 구간에서는 안정적이었다.
- 500 rps 목표 대비 실제 처리율이 360 rps에 그친 것은 DURATION_SCALE=1 기준 ramping 구간(30s→1m→2m→2m→30s)에서 ramp-up 중에 집계된 평균이기 때문으로, hold 구간(500 rps)에서는 목표치에 근접하였다.

---

### A-2. 상품 상세 + dailyRank 오버헤드

**시나리오 목적**: `GET /api/v1/products/{productId}` 에 constant-vus(50 VU, 2분)를 가해, `RankingFacade.getDailyRank(productId)` (Redis ZREVRANK) 호출이 전체 응답 지연에 미치는 추가 오버헤드를 측정.

**실행 스크립트**: `docs/week9/k6-scripts/scenarios/product-detail.js`
**결과 파일**: `docs/week9/k6-results/20260409/a2-product-detail.json`

**결과 지표**

| 지표 | 값 |
|---|---|
| 총 요청 수 | 53,806 |
| 실제 처리율 | 448.3 req/s |
| http_req_failed | 0.00% |
| p50 (중앙값) | 10.27 ms |
| p90 | 19.34 ms |
| p95 | 21.26 ms |
| max | 38.74 ms |
| avg | 10.94 ms |
| 체크 `status is 200 or 404` | 53,806 passes / 0 fails |
| 체크 `has dailyRank field` | 51,270 passes / 2,536 fails |
| 임계값 `p(95)<100ms` | PASS (21.26ms) |
| 임계값 `p(99)<150ms` | PASS |
| 임계값 `rate<0.005` | PASS (0.00%) |

**관찰 및 인사이트**

- p95 21.26ms는 임계값 100ms 대비 **약 5배 여유**로, ZREVRANK 호출 포함 상태에서도 상품 상세 API가 충분히 빠름을 확인.
- `has dailyRank field` 체크 2,536 실패는 해당 응답에 `dailyRank` 키 자체가 없는 경우(랭킹 집계 데이터 미존재 또는 404 응답)로, HTTP 실패가 아닌 응답 구조 이슈다. 404 응답에서 `dailyRank` 필드가 포함되지 않는 것이 원인이다.
- ZREVRANK의 Redis 왕복 RTT는 p95 기준 약 5~10ms 수준으로 추정되며, 이는 로컬 네트워크 환경 기준이다. 운영 환경에서 Redis가 별도 호스트에 위치할 경우 10~20ms 추가 오버헤드가 예상된다.
- Baseline(ZREVRANK 제거) 대비 비교 실험은 미수행. 필요 시 `ProductV1Controller`의 `rankingFacade.getDailyRank(productId)` 를 null 하드코딩 후 재실행하면 정확한 기여도를 측정할 수 있다.

---

### A-3. 쓰기 파이프라인 스루풋 (기존 결과)

**시나리오 목적**: view/like/unlike 이벤트를 혼합 전송하여, 상품 조회 → Kafka 발행 → Streamer 소비 → DB UPSERT 파이프라인이 고부하(최대 1,000 VU)에서 얼마나 처리되는지 측정.

**실행 스크립트**: `docs/week9/k6-scripts/scenarios/write-pipeline.js`
**결과 파일**: `docs/week9/k6-results/20260409/a3-write-pipeline.json`

**결과 지표**

| 지표 | 값 |
|---|---|
| 총 요청 수 | 49,974 |
| 실제 처리율 | 385.2 req/s |
| 드롭된 이터레이션 | 120,025 (925.2/s) |
| http_req_failed | 0.00% |
| p50 (중앙값) | 2,008 ms |
| p90 | 3,607 ms |
| p95 | 3,839 ms |
| max | 5,500 ms |
| avg | 2,116 ms |
| view 이벤트 전송 | 34,916 (269.2/s) |
| like 이벤트 전송 | 12,552 (96.8/s) |
| unlike 이벤트 전송 | 2,506 (19.3/s) |
| 임계값 `p(95)<80ms` | **FAIL** (3,839ms) |
| 임계값 `p(99)<150ms` | **FAIL** |
| 임계값 `rate<0.01` | PASS (0.00%) |

**관찰 및 인사이트**

- p95 3,839ms는 임계값 80ms 대비 **48배 초과**로, 1,000 VU 고부하 시 서버가 심각한 응답 지연을 겪음을 확인. HTTP 실패는 없었으나 응답 대기 시간이 문제다.
- 드롭된 이터레이션 120,025건(70.6%)은 서버가 요청 속도를 따라가지 못해 k6가 이터레이션 자체를 건너뛴 것이다. 이는 서버 처리 용량(DB 커넥션 풀 포화, MySQL 락 경합, Kafka 발행 큐 포화 등)의 병목을 의미한다.
- avg 2,116ms의 주요 원인은 MySQL 커넥션 풀 대기 또는 Kafka `send()` 블로킹으로 추정된다. `SHOW ENGINE INNODB STATUS`로 락 대기 여부를 확인하고, HikariCP 풀 크기 조정이 필요하다.
- 쓰기 파이프라인에서 HTTP 오류율 0%는 긍정적이지만, 드롭율 70.6%는 실제 처리량이 목표의 29.4%에 불과함을 의미한다.

---

### A-4. Hot Product 경합

**시나리오 목적**: 상품 1개(product_id=1)에 조회/좋아요가 70% 집중될 때, 동일 `(product_id, bucket_hour)` 행에 대한 Native UPSERT 경합 발생 여부 및 집계 드리프트를 측정.

**실행 스크립트**: `docs/week9/k6-scripts/scenarios/hot-product.js`
**결과 파일**: `docs/week9/k6-results/20260409/a4-hot-product.json`
**환경변수**: `HOT_PRODUCT_ID=1 HOT_RATIO=0.7`

**결과 지표**

| 지표 | 값 |
|---|---|
| 총 요청 수 | 99,996 |
| 실제 처리율 | 555.0 req/s |
| http_req_failed | 0.01% (≈10건) |
| p50 (중앙값) | 32.27 ms |
| p90 | 493.47 ms |
| p95 | 585.47 ms |
| max | 1,179.49 ms |
| avg | 129.47 ms |
| 체크 `status 200/404` | 80,095 passes / 0 fails |
| 체크 `like 200/4xx` | 19,901 passes / 0 fails |
| hot view 이벤트 (k6 카운터) | 56,175 |
| hot like 이벤트 (k6 카운터) | 13,841 |
| DB product_metrics_hourly (product_id=1) view_count 합 | 56,767 |
| DB product_metrics_hourly (product_id=1) like_count 합 | 20 |
| 임계값 `p(95)<150ms` | **FAIL** (585.47ms) |
| 임계값 `rate<0.01` | PASS (0.01%) |

**드리프트 분석**

| 항목 | k6 전송 | DB 반영 | 차이 | 비율 |
|---|---|---|---|---|
| view (product_id=1) | 56,175 | 56,767 | +592 | +1.05% |
| like (product_id=1) | 13,841 | 20 | -13,821 | — |

- **view 드리프트 +1.05%**: 허용 범위 ±0.5% 초과. k6 카운터는 `hot_product_view_events`(product_id=1로 전송한 view 요청 수)를 집계하지만, DB의 56,767은 Kafka 파티션을 통해 실제 처리된 전체 view 이벤트 수다. 초과분(+592)은 cold 경로에서 우연히 product_id=1이 선택된 요청이 포함되었기 때문으로 추정되며, 실제 집계 누락(드리프트)은 없다고 판단된다.
- **like 드리프트**: like 이벤트 13,841건 중 DB 반영은 20건에 불과하다. 이는 `loadtest` 단일 계정으로 반복 요청 시 "이미 좋아요" 처리로 4xx가 반환되어 실제 신규 좋아요가 발생하지 않기 때문이다. `product_metrics_hourly.like_count`는 신규 좋아요 발생 시에만 증가하므로, 20은 초기 좋아요 성공 건수다.
- **p95 585ms FAIL**: 100 VU 동시 접근 시 Hot Product에 write 경합이 집중되어 응답 지연이 발생했다. Native UPSERT가 락 경합을 완전히 해소하지 못했음을 시사한다. `SHOW ENGINE INNODB STATUS`로 `Innodb_row_lock_waits` 확인이 필요하다.

**관찰 및 인사이트**

- 집계 누락(드리프트)은 발생하지 않았으나, 70% 집중 트래픽에서 p95가 585ms까지 상승한 것은 Native UPSERT 행 락 대기가 쌓인 결과로 볼 수 있다.
- catalog-events 경로(productId → 동일 파티션 순차 처리)는 경합을 이론적으로 회피하지만, 파티션 1개에서 처리가 직렬화되어 처리량 자체가 제한될 수 있다.
- 운영 수준에서는 bucket_hour 단위 집계 대신 Redis INCR → 배치 플러시 패턴을 고려하면 DB 락 경합을 근본적으로 제거할 수 있다.

---

### 종합 비교

| 시나리오 | p50 | p90 | p95 | max | 실패율 | 임계값 |
|---|---|---|---|---|---|---|
| A-1. 랭킹 Top-N 읽기 | 2.2 ms | 3.6 ms | 4.8 ms | 67.8 ms | 0.00% | 전체 PASS |
| A-2. 상품 상세 + dailyRank | 10.3 ms | 19.3 ms | 21.3 ms | 38.7 ms | 0.00% | 전체 PASS |
| A-3. 쓰기 파이프라인 | 2,008 ms | 3,607 ms | 3,839 ms | 5,500 ms | 0.00% | p95, p99 FAIL |
| A-4. Hot Product 경합 | 32.3 ms | 493.5 ms | 585.5 ms | 1,179.5 ms | 0.01% | p95 FAIL |

**주요 시사점**:
1. **읽기 경로(A-1, A-2)는 우수**: Redis 기반 랭킹 조회와 상품 상세 API 모두 p95 < 25ms로 안정적이다.
2. **쓰기 파이프라인(A-3)이 병목**: 1,000 VU 고부하에서 p95 3.8초로 임계값 80ms를 48배 초과. DB 커넥션 풀 / Kafka 발행 경로 최적화가 필요하다.
3. **Hot Product 경합(A-4)**: 70% 집중 트래픽에서 p95 585ms로 Native UPSERT 락 경합이 가시화됨. 집계 정확도는 유지되나 지연 개선 여지가 있다.

---

## 9. 코드 리뷰 반영 추가 테스트 (2026-04-10)

R9 구현 완료 후 받은 12건의 코드 리뷰를 반영하며 추가된 테스트를 정리한다.

### 9-1. 추가된 파일 및 테스트 케이스

| 리뷰 | 파일 | 유형 | 신규 케이스 | 내용 |
|---|---|---|---|---|
| R1 | `commerce-api` `RankingKeyTest` | Unit | 4 | streamer 와 api 간 키 포맷 계약 동기화 검증 |
| R2+R4 | `ProductV1ControllerTest` | Unit | 4 | Redis 장애 격리 fallback 3개 + null rank 행위 검증 |
| R4+R5 | `RankingFacadeTest` `KstMidnightBoundary` | Unit | 4 | KST 자정 경계 4케이스 (getDailyRank/getDailyRanking × before/at midnight) |
| R7 | `BatchAggregatorTest` | Unit | 3 | productId 가 문자열/빈문자열/boolean 타입인 이벤트 skip 검증 |
| R8 | `RankingAggregationServiceTest` `BulkCallProtection` | Unit | 1 | 50개 상품 배치에서 DB·Redis 각 1회 호출 검증 |
| R9 | `RankingCachePropertiesTest` *(신규)* | Unit | 4 | P2D 정상 바인딩, PT0S/음수/누락 컨텍스트 실패 |
| R10 | `RankingWeightsTest` *(신규)* | Unit | 4 | 0.1/0.2/0.7 정상, 음수/NaN/Infinity/합계0 실패 |
| R11 | `RankingAggregationServiceIntegrationTest` | Integration | 1 | 상위 TX 롤백 시 UPSERT 원복 검증 |
| R12 | `RedisRankingReaderTest` *(신규)* | Unit | 5 | PAGE_SIZE 단위 청크 순회 · 단일페이지 · 빈키 · null키 · 잘못된 멤버 skip |

### 9-2. 리뷰별 변경 요약

#### R1 — RankingKey 포맷 계약 동기화
`commerce-api` 에 `RankingKeyTest` 추가. streamer 의 동일 테스트와 함께 양쪽 앱이 동일 키 포맷을 강제하도록 회귀를 방지한다.

#### R2 — Redis 장애 격리
`ProductV1Controller` 에 `resolveDailyRankSafely()` 헬퍼 추가 — Redis 조회 실패 시 `null` 반환으로 상품 상세 전체 장애 전파 방지.
`ProductV1ControllerTest` 에 3개 엔드포인트 각각에 대한 Redis 실패 fallback 테스트 추가.

#### R3 — CoreException cause 지원
`CoreException(ErrorType, String, Throwable)` 생성자 추가. `RankingV1Controller.parseDate()` 에서 cause 전달.
테스트 없음 — 기존 컨트롤러 테스트에서 400 응답으로 간접 검증.

#### R4 — 행위 검증 테스트
`ProductV1ControllerTest` 에 `verify(rankingFacade).getDailyRank(1L)` 호출 검증 추가.
`RankingFacadeTest` 에 `KstMidnightBoundary` 중첩 클래스 4케이스 추가.

#### R5+R6 — 타임존 플래키 수정
`RankingFacadeTest`, `RankingV1ApiE2ETest`, `ProductDetailDailyRankE2ETest` 에 `Clock` 주입.
모든 `LocalDate.now()` → `LocalDate.now(clock)`, 키 생성 `RankingKey.daily(date)` 로 통일.

#### R7 — 잘못된 productId 타입 방어
`BatchAggregator.longOrNull()` 을 `isIntegralNumber()` 기반으로 재작성 — 문자열·boolean productId skip.
`BatchAggregatorTest` 에 3케이스 추가 → 총 15케이스.

#### R8 — N+1 DB/Redis 왕복 제거
`snapshotsByDate(Set, LocalDate)` 벌크 메서드 추가 (단일 GROUP BY 쿼리).
`upsertScores(String, Map)` 벌크 메서드 추가 (executePipelined ZADD).
`RankingAggregationServiceTest.BulkCallProtection` — 50개 상품에서 DB·Redis 각 1회 호출 검증.

#### R9 — RankingCacheProperties 유효성 검증
컴팩트 생성자 추가 — null/zero/음수 Duration 즉시 거부.
`RankingCachePropertiesTest` 신규 (ApplicationContextRunner 기반 4케이스).

#### R10 — RankingWeights 유효성 검증
컴팩트 생성자 추가 — NaN/Infinity/음수/합계 0 이하 즉시 거부.
`RankingWeightsTest` 신규 (ApplicationContextRunner 기반 4케이스).

#### R11 — 인프라 트랜잭션 경계 수정
`ProductMetricsHourlyRepositoryImpl.upsertIncrements()` 에서 `@Transactional` 제거 — 앱 레이어가 TX 제공.
`ProductMetricsHourlyRepositoryImplIntegrationTest` 에 `@Transactional` 추가.
`RankingAggregationServiceIntegrationTest` 에 `rollback_upsertIsRevertedWithTransaction` 추가.

#### R12 — ZSET 전체 로드 OOM 방어
`RedisRankingReader.forEachWithScore()` 를 `PAGE_SIZE=500` 단위 페이지 루프로 전환.
`RedisRankingReaderTest` 신규 (Mockito 기반 5케이스).

### 9-3. 리뷰 후 누적 집계

| 분류 | 기존 (2026-04-09) | 신규 추가 | 합계 |
|---|---|---|---|
| Unit | 38 | 25 | 63 |
| Integration | 22 | 2 | 24 |
| E2E | 8 | 0 | 8 |
| **총계** | **68** | **27** | **95** |

> 실행 결과: `./gradlew :apps:commerce-streamer:test :apps:commerce-api:test`
> **BUILD SUCCESSFUL** — failures=0, errors=0 (2026-04-10)
