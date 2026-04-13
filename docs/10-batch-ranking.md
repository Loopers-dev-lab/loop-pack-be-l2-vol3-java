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

## 6. 구현 체크리스트

### Phase 1. product_metrics 구조 변경

- [ ] `metrics_date` 컬럼 추가 (DDL)
- [ ] UNIQUE 제약 변경: `product_id` → `(product_id, metrics_date)`
- [ ] `ProductMetricsEntity` — `metricsDate` 필드 추가
- [ ] `ProductMetricsJpaRepository` — increment 쿼리에 `metrics_date` 조건 추가
- [ ] `ProductMetricsProcessor` — `occurredAt.toLocalDate()`를 날짜 기준으로 upsert

### Phase 2. MV 테이블 생성

- [ ] `mv_product_rank_weekly` DDL 작성
- [ ] `mv_product_rank_monthly` DDL 작성

### Phase 3. Spring Batch Job

- [ ] `RankingWeeklyJobConfig` — Chunk-Oriented Job 구현
- [ ] `RankingMonthlyJobConfig` — Chunk-Oriented Job 구현
- [ ] `ProductMetricsItemReader` — 날짜 범위 기반 페이징 Reader
- [ ] `RankingItemProcessor` — score 계산 및 순위 부여
- [ ] `MvRankingItemWriter` — MV 테이블 upsert Writer

### Phase 4. Ranking API 확장

- [ ] `RankingV1Controller` — `period` 파라미터 추가
- [ ] `RankingFacade` — period별 분기 처리
- [ ] `RankingRepository` — 주간/월간 MV 조회 메서드 추가
