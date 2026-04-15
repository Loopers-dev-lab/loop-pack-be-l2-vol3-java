# 주간/월간 랭킹 시스템 (Spring Batch)

## 1. 왜 Batch인가 — 실시간 처리의 한계

### 문제

일간 랭킹은 Redis ZSET에 이벤트가 발생할 때마다 점수를 실시간으로 누적하는 방식으로 구현되어 있다.

그런데 **주간/월간 랭킹**을 같은 방식으로 처리하면 어떻게 될까?

- 7일치, 30일치 Redis ZSET 키를 `ZUNIONSTORE`로 합산 → 키가 많아질수록 연산 비용 증가
- Redis TTL(현재 2일)을 늘리면 메모리 사용량 급증
- 매 요청마다 합산 연산을 하면 응답 지연 발생

주간/월간 랭킹은 **정확성이 중요하고, 수 초 단위의 실시간성은 필요하지 않다.** 하루에 한 번 집계해도 충분한 데이터다. 이런 경우 Spring Batch로 주기적으로 집계해두는 것이 적합하다.

| 항목 | 실시간 (Redis ZSET) | 배치 (Spring Batch) |
|---|---|---|
| 반영 속도 | 즉시 | 주기적 (1일 1회) |
| 집계 비용 | 이벤트마다 분산 처리 | 한 번에 대량 처리 |
| 적합한 단위 | 일간 | 주간, 월간 |
| 인프라 부담 | 메모리(Redis) | DB I/O |

---

## 2. product_metrics 구조 변경

### 문제

기존 `product_metrics` 테이블은 날짜 없이 상품별 **전체 누적값**만 저장한다.

```
product_metrics
id | product_id | like_count | view_count | sales_count
-- | ---------- | ---------- | ---------- | -----------
1  | 101        | 150        | 800        | 30
```

배치가 "이번 주 랭킹"을 계산하려면 이번 주에 발생한 이벤트만 집계해야 한다. 그런데 이 테이블로는 불가능하다.

```
like_count = 150
→ 이 중에서 이번 주에 발생한 게 몇 건인지 알 수 없음
```

### 결정: metrics_date 컬럼 추가

별도 테이블(`product_metrics_daily`)을 만드는 방법도 있지만, 과제 대상 테이블이 `product_metrics`로 명시되어 있고 변경 범위를 최소화하는 방향으로 **기존 테이블에 날짜 컬럼만 추가**한다.

```
product_metrics (변경 후)
id | product_id | metrics_date | like_count | view_count | sales_count
-- | ---------- | ------------ | ---------- | ---------- | -----------
1  | 101        | 2026-04-07   | 10         | 80         | 3
2  | 101        | 2026-04-08   | 25         | 120        | 7
3  | 101        | 2026-04-13   | 30         | 95         | 5
```

- `product_id + metrics_date` 조합이 UNIQUE (기존 `product_id` 단독 UNIQUE 제거)
- 하루에 상품당 레코드 1개
- 값은 **그날 하루 발생한 증분**

이렇게 하면 주간 집계가 단순한 날짜 범위 SUM으로 해결된다.

```sql
SELECT product_id, SUM(like_count + view_count + sales_count) AS score
FROM product_metrics
WHERE metrics_date BETWEEN '2026-04-07' AND '2026-04-13'
GROUP BY product_id
ORDER BY score DESC
LIMIT 100;
```

---

## 3. Materialized View 설계

### 왜 MV인가

주간/월간 집계 쿼리를 매 API 요청마다 실행하면 DB 부하가 크다. 배치로 미리 계산한 결과를 별도 테이블에 저장해두고 API는 이 테이블만 읽는다. MySQL은 MV 기능이 없으므로 **별도 테이블 + 배치 적재** 방식으로 구현한다.

### 테이블 구조

```sql
-- 주간 TOP 100
CREATE TABLE mv_product_rank_weekly (
  product_id    BIGINT PRIMARY KEY,
  score         DOUBLE NOT NULL,
  year_week     VARCHAR(8) NOT NULL,  -- e.g. 20260414 (해당 주 월요일)
  rank          INT NOT NULL,
  updated_at    DATETIME NOT NULL
);

-- 월간 TOP 100
CREATE TABLE mv_product_rank_monthly (
  product_id    BIGINT PRIMARY KEY,
  score         DOUBLE NOT NULL,
  year_month    VARCHAR(6) NOT NULL,  -- e.g. 202604
  rank          INT NOT NULL,
  updated_at    DATETIME NOT NULL
);
```

---

## 4. Spring Batch Job 설계

### Chunk-Oriented Processing

`product_metrics`에서 날짜 범위로 읽어 MV 테이블에 적재한다.

```
ItemReader  → product_metrics에서 해당 기간 데이터 읽기 (chunk 단위)
ItemProcessor → score 계산 (가중치 합산), 순위 부여
ItemWriter  → mv_product_rank_weekly / mv_product_rank_monthly upsert
```

### Job 파라미터

- `targetDate` : 집계 기준 날짜 (e.g. `20260413`)
- `period` : `weekly` 또는 `monthly`

```
targetDate=20260413, period=weekly
→ 2026-04-07 ~ 2026-04-13 기간 집계
```

### 점수 공식

일간 랭킹과 동일한 가중치를 사용한다.

```
score = 0.1 * view_count + 0.2 * like_count + 0.7 * log1p(total_quantity)
```

일간/주간/월간 모두 동일한 기준으로 점수를 산정해야 랭킹 간 일관성이 유지된다.
이를 위해 `product_metrics`에 `total_quantity`(수량 합산)를 추가로 저장한다.

---

## 5. Ranking API 확장

기존 API에 `period` 파라미터를 추가한다.

```
GET /api/v1/rankings?date=20260413&period=daily&size=20&page=1    → Redis ZSET
GET /api/v1/rankings?date=20260413&period=weekly&size=20&page=1   → mv_product_rank_weekly
GET /api/v1/rankings?date=20260413&period=monthly&size=20&page=1  → mv_product_rank_monthly
```

- `period` 기본값은 `daily` (기존 동작 유지)
- `date` 파라미터는 세 가지 모드 모두 기준 날짜로 사용

---

## 7. 설계 시 고민한 이슈들

### 배치 실패 시 랭킹 공백
배치가 실패하면 MV 테이블이 갱신되지 않아 이전 데이터가 그대로 노출된다.
Spring Batch는 동일 파라미터로 재실행 시 실패 지점부터 이어서 실행하는 기능을 제공한다.
자동 재시도를 구성할 경우 최대 재시도 횟수 + Exponential Backoff + 실패 알림을 세트로 설계해야 한다.

### 배치 실행 타이밍
매일 새벽(예: 새벽 1시)에 실행하는 게 자연스럽다. 하루치 `product_metrics`가 모두 쌓인 이후에 집계해야 정확하기 때문이다.
단, 배치 실행 이후 자정까지 들어온 이벤트는 다음 배치까지 반영되지 않는 지연이 발생한다.

### 주간/월간 기준 정의
집계 기간을 어떻게 정의하느냐에 따라 결과가 달라진다.

| 방식 | 설명 | 적합한 경우 |
|---|---|---|
| 최근 N일 | 오늘 기준 7일/30일 전 ~ 오늘, 매일 바뀜 | 트렌드 중심 서비스 |
| 고정 기간 | 월요일~일요일, 1일~말일 고정, 기간 시작일에 한 번에 바뀜 | 커머스 큐레이션, 마케팅 연동 |

커머스 서비스 특성상 고정 기간 방식이 더 자연스러울 수 있으나, 비즈니스 요구사항에 따라 결정해야 한다.

### 동점 처리
두 상품의 점수가 동일할 때 순위 결정 기준이 명확하지 않으면 배치를 돌릴 때마다 순위가 달라질 수 있다.
Redis ZSET은 동점 시 사전순으로 처리하지만, DB 집계는 기준이 없으면 비결정적이다.

### 어뷰징 방지
배치는 기간 내 데이터를 단순 합산하는 구조라, 배치 실행 직전 단기간에 대량 이벤트를 발생시켜 랭킹을 인위적으로 올리는 어뷰징에 취약하다.

### 월간 집계를 주간 MV에서 하지 않는 이유

월간 배치가 `mv_product_rank_weekly`를 입력으로 쓰는 방식(체인 구조)을 고려했으나 채택하지 않았다.

**이유 1 — score 합산 불가**
현재 점수 공식에 `log1p`가 포함되어 있어 주간 score를 단순 합산하면 월간 score와 달라진다.
```
log1p(3) + log1p(4) ≠ log1p(7)
→ 주간 score 합산 ≠ 월간 score
```
원본 count를 함께 저장하면 해결되지만, MV 테이블의 역할(랭킹 조회)과 책임이 섞인다.

**이유 2 — TOP 100 절단 문제**
`mv_product_rank_weekly`는 TOP 100만 저장한다. 주간 TOP 100에 한 번도 들지 못한 상품은 월간 집계에서 누락된다.

**이유 3 — 스케줄러 의존성**
월간 배치가 주간 배치 완료 후에만 실행될 수 있어 순서 의존성이 생긴다. 주간 배치 장애 시 월간 배치도 영향을 받는다.

**결론**
`product_metrics`(일별 원본)에서 weekly, monthly가 각자 독립적으로 집계하는 구조(fan-out)를 채택했다. 단일 원천에서 파생되므로 재집계가 단순하고 스케줄러 간 의존성이 없다.

### product_metrics Reader에 JPA 대신 JDBC를 사용한 이유

`commerce-batch`는 `commerce-streamer`를 의존하지 않는다. `ProductMetricsEntity`는 `commerce-streamer` 모듈에 정의되어 있어 batch 모듈의 클래스패스에 존재하지 않는다.

JPA(JPQL)는 Entity 클래스를 직접 참조하기 때문에 클래스가 없으면 컴파일 에러가 발생한다.
```
FROM ProductMetricsEntity e  -- ProductMetricsEntity.class가 있어야 함
```

JDBC는 테이블 이름을 문자열로 참조하므로 Java 클래스 없이 DB 연결만으로 읽기가 가능하다.
```sql
FROM product_metrics  -- 테이블명 문자열, 클래스 불필요
```

batch 모듈에 `ProductMetricsEntity`를 중복 정의하는 방법도 있으나, 같은 Entity를 두 모듈에서 관리하면 스키마 변경 시 동기화 누락 위험이 있다. JDBC를 사용하면 이 문제를 피할 수 있다.

---

## 6. 구현 체크리스트

### Phase 1. product_metrics 구조 변경

- [x] `metrics_date` 컬럼 추가 (DDL)
- [x] `total_quantity` 컬럼 추가 (DDL) — 주문 수량 합산, 일간과 동일한 점수 기준 유지
- [x] UNIQUE 제약 변경: `product_id` → `(product_id, metrics_date)`
- [x] `ProductMetricsEntity` — `metricsDate`, `totalQuantity` 필드 추가
- [x] `ProductMetricsJpaRepository` — increment 쿼리에 `metrics_date` 조건 추가, `upsertIfAbsent` 추가, `incrementSalesAndQuantity`로 통합
- [x] `ProductMetricsProcessor` — `occurredAt.toLocalDate()`를 날짜 기준으로 upsert, ORDER_CONFIRMED 시 `incrementSalesAndQuantity` 호출

### Phase 2. MV 테이블 생성

- [x] `MvProductRankWeeklyEntity` JPA Entity 생성 (DDL 대체)
- [x] `MvProductRankMonthlyEntity` JPA Entity 생성 (DDL 대체)

### Phase 3. Spring Batch Job

- [x] `RankingWeeklyJobConfig` — Chunk-Oriented Job 구현 (Step1: 점수 계산, Step2: 순위 확정)
- [x] `RankingMonthlyJobConfig` — Chunk-Oriented Job 구현 (Step1: 점수 계산, Step2: 순위 확정)
- [x] `ProductMetricsItemReader` — 날짜 범위 기반 페이징 Reader (period 파라미터로 weekly/monthly 날짜 범위 계산)
- [x] `RankingItemProcessor` — score 계산 (`score = 0.1*view + 0.2*like + 0.7*log1p(qty)`), 출력: `RankedProductDto`
- [x] `WeeklyMvRankingItemWriter` — weekly MV 테이블 upsert Writer
- [x] `MonthlyMvRankingItemWriter` — monthly MV 테이블 upsert Writer
- [x] `WeeklyRankAssignTasklet` — weekly 순위 부여 및 TOP 100 정리 (원래 계획의 `MvRankingItemWriter`에서 분리)
- [x] `MonthlyRankAssignTasklet` — monthly 순위 부여 및 TOP 100 정리 (원래 계획의 `MvRankingItemWriter`에서 분리)
- [x] `RankedProductDto` — Processor 출력 DTO (productId, score)
- [x] `ProductMetricsAggregatedDto` — Reader 출력 DTO (productId, 집계된 view/like/quantity)
- [x] `MvProductRankWeeklyJpaRepository` — weekly MV 조회/저장 Repository
- [x] `MvProductRankMonthlyJpaRepository` — monthly MV 조회/저장 Repository

### Phase 4. Ranking API 확장

- [ ] `RankingV1Controller` — `period` 파라미터 추가
- [ ] `RankingFacade` — period별 분기 처리
- [ ] `RankingRepository` — 주간/월간 MV 조회 메서드 추가
