# 10. 배치 랭킹 시스템 설계 — MV 기반 주간/월간 랭킹

> Spring Batch로 product_metrics를 기간 집계하여 MV 테이블에 TOP 100 랭킹을 적재하고,
> API에서 주간/월간 요청 시 MV를 primary 소스로 조회하는 시스템.

---

## 요구사항

### 과제 요구사항 (10-batch-ranking-quests.md)

| # | 요구사항 | 상세 | Checklist |
|---|---------|------|-----------|
| **R1** | Spring Batch Job 구현 | `product_metrics`를 **Chunk-Oriented** 방식으로 집계 처리 | Job을 작성하고 **파라미터 기반**으로 동작시킬 수 있다 |
| **R2** | Materialized View 설계 | `mv_product_rank_weekly` (주간 TOP 100), `mv_product_rank_monthly` (월간 TOP 100) | MV 구조를 설계하고 **올바르게 적재**했다 |
| **R3** | Ranking API 확장 | 기존 `GET /api/v1/rankings`에서 **기간 정보**를 받아 일간/주간/월간 랭킹 제공 | 조회 형태에 따라 **적절한 데이터 소스** 기반 랭킹 제공 |
| **R4** | Technical Writing | 블로그 + 10주 회고 (TL;DR 포함, "왜 그렇게 판단했는가" 중심) | — |

### 기존 구현 현황 (Round 9)

| 항목 | 상태 | 비고 |
|------|------|------|
| commerce-batch 모듈 | ✅ | 6개 Job 운영 중 |
| product_metrics 테이블 | ✅ | PK: (product_id, metric_date), daily grain |
| RankingCorrectionJob | ✅ | Chunk 1,000, JdbcCursorItemReader → Redis |
| Redis 일간/주간/월간 ZSET | ✅ | carry-over + ZUNIONSTORE |
| Ranking API (scope 파라미터) | ✅ | daily/weekly/monthly → **모두 Redis 조회** |
| MV 테이블 | ❌ | **Round 10 핵심 과제** |

---

## 설계 결정 요약

| 질문 | 결정 | 근거 |
|------|------|------|
| 시간 윈도우 | **슬라이딩 윈도우 (매일 갱신)** | Redis weekly와 동일한 시간 범위. 무신사 방식. 사용자에게 매일 갱신되는 랭킹 제공 |
| Score 계산 방식 | **방식 A — 메트릭 균등 합산 후 score 1회 계산** | MV는 "기간 총 실적" 관점. Redis(지수 감쇠)와 다른 관점을 제공하는 것이 MV의 존재 이유 |
| Reader | **JdbcCursorItemReader + Partitioning** | GROUP BY 집계에서 Paging은 페이지마다 재실행하므로 부적합. Cursor의 멀티스레드 한계를 Partitioning으로 극복 |
| 비즈니스 로직 위치 | **Reader SQL에서 집계, Java ItemProcessor에서 score 계산** | Score 공식 중앙화(ScoreFormula)를 위해 SQL에서 Java로 이동. categoryPriority 누락 해결 |
| Writer 전략 | **DELETE + INSERT (스테이징 경유)** | 병렬 집계 → 스테이징 → mergeStep에서 Global TOP 100 |
| 멱등성 | **cleanup(DELETE MV + 스테이징) → 전체 재실행** | 스테이징 정합성을 위해 부분 재실행보다 전체 재실행이 안전 |
| Job Instance 동일성 | **RunIdIncrementer** | targetDate, scope 파라미터 보존 + run.id 증가로 재실행 허용. cleanupStep이 멱등성 보장 |
| Redis vs MV 역할 | **daily → Redis, weekly/monthly → MV 단일 소스 (Redis fallback 없음)** | 다른 공식(감쇠 vs 균등)으로 계산한 결과를 fallback으로 쓰면 데이터 일관성이 깨짐. MV 배치 실패 시에는 "빈 결과 + 알림"이 "다른 순위 노출"보다 안전 |
| Job 구조 | **scope 파라미터로 주간/월간 분기하는 단일 Job** | Job Config 중복 방지. 회사 코드의 batchTyp 패턴 참고 |

---

## 시간 윈도우 전략

### 슬라이딩 윈도우 (매일 갱신)

MV는 캘린더 기반(월~일, 1일~말일)이 아닌, **매일 갱신되는 슬라이딩 윈도우**로 집계한다.

```
targetDate = 2026-04-16 기준:

주간: 2026-04-10 ~ 2026-04-16 (최근 7일)
      ├─ 다음날 실행 시: 2026-04-11 ~ 2026-04-17 (1일 슬라이드)
      └─ 매일 갱신되어 "오늘 기준 최근 7일" 유지

월간: 2026-03-18 ~ 2026-04-16 (최근 30일)
      ├─ 다음날 실행 시: 2026-03-19 ~ 2026-04-17 (1일 슬라이드)
      └─ 매일 갱신되어 "오늘 기준 최근 30일" 유지
```

**선택 근거**:
- Redis weekly도 슬라이딩 7일 (ZUNIONSTORE 최근 7일 daily). MV와 시간 범위가 일치해야 fallback이 의미 있음
- 무신사 등 이커머스에서 주간/월간 랭킹도 매일 갱신하는 것이 UX에 유리
- period_key는 targetDate 자체 (`20260416`) — "이 날짜 기준 최근 N일" 의미

### Redis monthly(지수 감쇠)와 MV monthly(균등 합산)의 차이

Redis monthly는 **지수 감쇠** 방식이다:

```
내일_monthly = 오늘_monthly × 0.97 + 오늘_daily × 1.0
```

이를 30일간 풀어쓰면:

```
monthly = Σ(i=0 ~ 29) daily_(today-i) × 0.97^i

= daily_today     × 0.97⁰   (= 1.000)
+ daily_1일전     × 0.97¹   (= 0.970)
+ daily_2일전     × 0.97²   (= 0.941)
+ ...
+ daily_29일전    × 0.97²⁹  (= 0.413)
```

**주의**: Redis는 "딱 30일"이 아니라 서비스 시작 이후 **모든 날**이 반영된다.
다만 0.97을 계속 곱하므로 오래된 날일수록 가중치가 0에 수렴한다.
가중치가 절반이 되는 데 걸리는 일수(**반감기**) ≈ 23일 (`ln(0.5) / ln(0.97) ≈ 22.8`).

```
일수    가중치(0.97^i)   누적 기여
 0일    1.000            5.0%     (오늘)
 6일    0.833           32.9%     ← 최근 7일이 전체의 1/3
13일    0.673           57.4%     ← 최근 14일이 전체의 57%
22일    0.502           80.2%     ← 반감기: 23일 전 = 50%
29일    0.413          100.0%
```

**MV 방식 A(균등 합산)와의 차이**:

```
Redis monthly (지수 감쇠, 윈도우 없음):
  상품 A: 30일 전 매출 1000만원 → 가중치 0.40으로 반영
  상품 B: 오늘 매출 1000만원     → 가중치 1.00으로 반영
  → B가 유리 (최근 활동 우대)

MV 방식 A (균등 합산, 30일 고정 윈도우):
  상품 A: 30일 전 매출 1000만원 → 가중치 1.0
  상품 B: 오늘 매출 1000만원     → 가중치 1.0
  → 동일 (기간 내 총량만 평가)
```

**두 방식은 관점이 다르다:**
- Redis: "최근에 뜨는 상품" (트렌드)
- MV: "기간 총 실적이 높은 상품" (누적 성과)

MV가 Redis와 동일한 지수 감쇠를 쓰면 MV를 만들 이유가 없다. 다른 관점을 제공하는 것이 MV의 존재 가치다.

---

## 아키텍처

### 전체 데이터 흐름

```
[product_metrics (DB 원장, daily grain)]
  │
  │ Reader: GROUP BY product_id, SUM(최근 7일 or 30일)
  ▼
[상품별 기간 메트릭 균등 합계]
  │
  │ Processor: Score v2 공식 (log₁₀ 정규화 + tiebreaker)
  ▼
[상품별 score → 정렬 → TOP 100]
  │
  │ Writer: DELETE period_key → INSERT TOP 100
  ▼
[mv_product_rank_weekly / mv_product_rank_monthly]
  │
  │ API: SELECT WHERE period_key = ? ORDER BY ranking
  ▼
[클라이언트]
```

### Redis와 MV의 역할 분담 — 단일 소스 원칙

```
[API 요청]
  │
  ├── scope=daily   → Redis ZSET (단일 소스)
  │
  ├── scope=weekly  → MV 테이블 (단일 소스, 균등 합산)
  │
  └── scope=monthly → MV 테이블 (단일 소스, 균등 합산)
```

**Redis fallback을 두지 않는 이유**: Redis(지수 감쇠)와 MV(균등 합산)는 다른 공식으로 계산하므로 같은 기간에 대해 순위가 다르다. MV 배치 실패 시 Redis fallback으로 전환하면 "어제는 A가 1위, 오늘은 B가 1위"라는 데이터 불일치가 발생한다.

**전일 MV fallback**: 당일 MV가 없으면 전일 MV를 반환한다. 같은 공식, 같은 소스에서 계산한 결과이므로 데이터 불일치가 아니라 1일 시간 지연일 뿐이다 (7일 중 6일 겹침). MV는 carry-over(누적)가 아니라 매번 원장에서 기간 전체를 새로 집계하므로, 전일 MV는 독립적으로 계산된 정확한 결과다.

**데이터 보존 정책**: cleanupStep에서 당일 period_key만 삭제하고, 3일 이전 데이터를 별도 정리한다. 전일/전전일 MV가 fallback으로 사용 가능하도록 보존.

**기존 Redis weekly/monthly**: MV 도입 검증 완료 후 carry-over 스케줄러에서 weekly/monthly 생성 로직 제거. daily carry-over만 유지.

### 전체 재계산 vs 증분 계산 — 왜 매번 원장에서 새로 계산하는가

MV는 매일 원장(product_metrics)에서 기간 전체를 GROUP BY로 새로 집계한다. "어제 결과에서 가장 오래된 날을 빼고 오늘을 더하는" 증분 방식이 더 효율적이지 않은가?

**증분 계산을 채택하지 않은 이유: Late-Arriving Fact**

이커머스에서 주문 취소/환불은 원주문과 다른 날에 발생한다. product_metrics의 cancel_by_order_date는 원주문 날짜의 행에 기록되므로, **이미 지나간 날의 데이터가 사후에 변경된다**:

```
4/10: 상품 A 주문 100건 (1000만원)
4/15: 그 중 30건 취소 → product_metrics 4/10 행의 cancel_by_order_date 갱신

증분 계산: 4/10의 값은 이미 어제 MV에 반영됨 → 사후 변경을 감지 못함
전체 재계산: 4/10~4/16 전체를 다시 읽으므로 → 변경된 값이 자동 반영
```

| 시나리오 | 전체 재계산 | 증분 계산 |
|---------|-----------|----------|
| 정상 주문 | ✅ 정확 | ✅ 정확 |
| 지연 취소 (주문 후 며칠 뒤) | ✅ 자동 반영 | ❌ 원주문 날짜 변경 감지 못함 |
| 운영팀 데이터 보정 | ✅ 다음 배치 자동 반영 | ❌ 전체 재계산을 별도 실행해야 함 |
| 오류 전파 | ❌ 없음 | ⚠️ 어제 MV가 틀리면 오늘도 틀림 |

증분 계산은 "과거 데이터가 불변"이라는 전제가 필요하다. product_metrics의 Late-Arriving Fact 설계가 이 전제를 깨뜨리므로, 전체 재계산이 이커머스 랭킹에 더 적합하다.

성능 차이(Partitioning 4 Worker 기준 ~10초 vs ~3초)는 1일 1회 배치에서 운영 영향이 없다. 증분이 유리해지는 전환점은 배치 주기가 5분 이하로 빈번해질 때다.

---

## MV 테이블 스키마

### mv_product_rank_weekly

```sql
CREATE TABLE mv_product_rank_weekly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    ranking INT NOT NULL,
    score DOUBLE NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    sales_count BIGINT NOT NULL DEFAULT 0,
    sales_amount BIGINT NOT NULL DEFAULT 0,
    period_key VARCHAR(8) NOT NULL,   -- '20260416' (targetDate, 슬라이딩 윈도우 기준일)
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_period_ranking (period_key, ranking)
) ENGINE=InnoDB;
```

### mv_product_rank_monthly

```sql
CREATE TABLE mv_product_rank_monthly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    ranking INT NOT NULL,
    score DOUBLE NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    sales_count BIGINT NOT NULL DEFAULT 0,
    sales_amount BIGINT NOT NULL DEFAULT 0,
    period_key VARCHAR(8) NOT NULL,   -- '20260416' (targetDate, 슬라이딩 윈도우 기준일)
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_period_ranking (period_key, ranking)
) ENGINE=InnoDB;
```

**설계 판단**:
- **PK**: AUTO_INCREMENT id. DELETE+INSERT 전략이므로 단순한 PK가 유리
- **period_key**: targetDate 문자열 (`20260416`). "이 날짜 기준 최근 7일/30일" 의미. 슬라이딩 윈도우이므로 매일 새로운 period_key 생성
- **인덱스**: `(period_key, ranking)` — API 조회 패턴 `WHERE period_key = ? ORDER BY ranking` 에 최적화
- **개별 메트릭 저장**: score뿐 아니라 view_count, like_count 등도 저장 — 분석/디버깅 및 향후 다차원 정렬 확장 용도
- **이전 기간 데이터**: 당일 기준 period_key만 유지. 이전 날짜 데이터는 CleanupTasklet에서 삭제 (또는 보존 후 별도 정리 Job)

---

## Spring Batch Job 설계

### 설계 판단의 흐름

1. Chunk vs Tasklet → **Chunk**: 프레임워크 운영 기능(retry, 모니터링, restart) 활용
2. CursorReader vs PagingReader → **CursorReader**: GROUP BY 집계 쿼리에서 Paging은 페이지마다 집계를 재실행하므로 부적합
3. CursorReader는 멀티스레드 불가(ResultSet 공유 상태) → **Partitioning**: CursorReader의 장점(1회 쿼리)을 유지하면서 병렬 처리
4. Partitioning + Global TOP 100 → **3-Step 구조**: 병렬 집계(스테이징) → 글로벌 머지(TOP 100)

### Job 구조 (Partitioning + Map-Reduce)

```
ProductRankingMvJob
  ├── Parameter: targetDate (yyyyMMdd), scope (weekly|monthly)
  │
  ├── Step 1: cleanupStep (Tasklet)
  │   └── DELETE FROM mv_product_rank_{scope} WHERE period_key = :periodKey
  │   └── DELETE FROM mv_product_rank_staging WHERE period_key = :periodKey
  │   └── allowStartIfComplete(true)
  │   └── on("FAILED").end()
  │
  ├── Step 2: partitionedAggregateStep (Partitioned Chunk, 병렬)
  │   │
  │   │  [Partitioner] product_id 범위를 gridSize(기본 4)개로 분할
  │   │  TaskExecutor: SimpleAsyncTaskExecutor (gridSize 스레드)
  │   │
  │   ├── [Worker 1] product_id :minId ~ :maxId
  │   │   ├── Reader: JdbcCursorItemReader (GROUP BY 집계, 해당 범위만)
  │   │   ├── Processor: ScoreFormula.calculate() → score 계산 + categoryPriority 반영
  │   │   ├── Writer: JdbcBatchItemWriter → 스테이징 테이블 INSERT
  │   │   └── faultTolerant + retry(3) + ExponentialBackOffPolicy
  │   │
  │   ├── [Worker 2] ... (동일 구조, 다른 범위)
  │   ├── [Worker 3] ...
  │   └── [Worker N] ...
  │
  └── Step 3: mergeStep (Tasklet)
      └── INSERT INTO mv_product_rank_{scope}
          SELECT ..., ROW_NUMBER() OVER (ORDER BY score DESC) AS ranking
          FROM mv_product_rank_staging
          WHERE period_key = :periodKey
          ORDER BY score DESC
          LIMIT 100
```

### 왜 Partitioning인가

**요구사항**: "대량의 데이터를 읽고 처리할 수 있도록 구성"

쿠팡급(상품 100만, 30일치 3,000만 행) 기준 성능:

| 구조 | GROUP BY 실행 | 소요 시간 |
|------|-------------|----------|
| 단일 CursorReader | 3,000만 행 1회 | ~30초 |
| **Partitioning (4 Worker)** | 각 750만 행 × 4 병렬 | **~10초** (3배 빠름) |
| Partitioning (10 Worker) | 각 300만 행 × 10 병렬 | **~5초** (6배 빠름) |

CursorReader의 장점(GROUP BY 1회 실행)을 유지하면서, 데이터를 product_id 범위로 분할하여 병렬 처리한다. PagingReader로 전환하면 페이지마다 GROUP BY를 재실행하는 문제가 생기지만, Partitioning은 각 Worker가 **독립 커넥션 + 독립 CursorReader**를 가지므로 이 문제가 없다.

### 왜 Chunk인가 — 프레임워크 운영 기능 활용

이 작업은 Tasklet(INSERT INTO...SELECT)으로도 가능하고, 네트워크 효율만 따지면 Tasklet이 우위다. chunk를 선택하면 Spring Batch가 제공하는 운영 기능을 활용할 수 있다:

- **faultTolerant + retry + ExponentialBackOffPolicy**: 일시적 DB 에러(데드락, 커넥션 타임아웃) 시 자동 재시도. 100ms → 200ms → 400ms 간격으로 재시도하여 락 해소 시간 확보
- **StepExecution 자동 기록**: 각 Worker별 readCount, writeCount 자동 추적
- **StepMonitorListener**: Worker 실패 시 알림
- **Partitioned restart**: 실패한 파티션만 재실행 가능

### 스테이징 테이블

```sql
CREATE TABLE mv_product_rank_staging (
    product_id BIGINT NOT NULL,
    score DOUBLE NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    sales_count BIGINT NOT NULL DEFAULT 0,
    sales_amount BIGINT NOT NULL DEFAULT 0,
    period_key VARCHAR(8) NOT NULL,
    PRIMARY KEY (product_id, period_key)
) ENGINE=InnoDB;
```

각 Worker가 자기 범위의 전체 집계 결과를 스테이징에 적재. PK가 `(product_id, period_key)`이므로 Worker 간 충돌 없음 (product_id 범위가 겹치지 않으므로).

### Worker Reader SQL (파티션별)

```sql
SELECT
    pm.product_id,
    SUM(pm.view_count) AS total_view_count,
    SUM(pm.like_count - pm.unlike_count) AS total_net_like_count,
    SUM(pm.sales_count) AS total_sales_count,
    SUM(pm.sales_amount - pm.cancel_amount_by_event_date) AS total_net_sales_amount,
    p.category_id
FROM product_metrics pm
JOIN product p ON pm.product_id = p.id
WHERE pm.metric_date BETWEEN :startDate AND :endDate
  AND pm.product_id BETWEEN :minProductId AND :maxProductId
  AND p.deleted_at IS NULL
GROUP BY pm.product_id, p.category_id
```

- **주간**: `startDate = targetDate - 6`, `endDate = targetDate` (7일)
- **월간**: `startDate = targetDate - 29`, `endDate = targetDate` (30일)
- **LIMIT 없음**: 각 파티션의 전체 결과를 스테이징에 적재. 글로벌 TOP 100은 mergeStep에서 결정
- **product_id BETWEEN**: Partitioner가 할당한 범위만 처리
- **score 계산은 SQL이 아닌 Java ItemProcessor에서 수행**: `ScoreFormula.calculate()` 호출

### Worker Processor — ScoreFormula 위임

Reader에서 집계된 `AggregatedMetricsRow`를 받아 `ScoreFormula.calculate()`로 score를 계산한다.
Score 공식을 SQL에서 제거하고 Java ItemProcessor로 이동한 이유:

1. **Score 공식 중앙화**: `ScoreFormula`(modules/jpa)가 유일한 공식 정의. streamer, batch correction, MV Job 3곳이 모두 이 클래스에 위임
2. **categoryPriority 반영**: SQL에서는 `categoryPriority` 매핑(yml 설정)을 적용할 수 없어 누락되어 있었음. Java Processor에서 `resolveCategoryPriority()`를 통해 반영
3. **가중치 변경 시 단일 수정 지점**: `ScoreFormula.Weights`로 통일되어 공식 변경 시 한 곳만 수정

### mergeStep SQL

```sql
INSERT INTO mv_product_rank_{scope}
    (product_id, ranking, score, view_count, like_count, sales_count, sales_amount, period_key, created_at)
SELECT
    product_id,
    ROW_NUMBER() OVER (ORDER BY score DESC) AS ranking,
    score, view_count, like_count, sales_count, sales_amount, :periodKey, NOW()
FROM mv_product_rank_staging
WHERE period_key = :periodKey
ORDER BY score DESC
LIMIT 100
```

스테이징에 모인 전체 결과에서 `ROW_NUMBER()`로 글로벌 순위를 부여하고 TOP 100만 MV에 적재.

### Best Practice 대조 점검

| Best Practice | 적용 | 상세 |
|-------------|------|------|
| @StepScope + Late Binding | ✅ | Worker Reader에 minProductId, maxProductId, targetDate, scope 주입 |
| Reader name 설정 | ✅ | 각 Worker별 고유 name. ExecutionContext 저장 시 key |
| Processor에서 DB 수정 금지 | ✅ | ScoreFormula.calculate()로 score 계산만 수행. DB 수정 없음 |
| Writer 벌크 처리 | ✅ | JdbcBatchItemWriter (JDBC batch INSERT) |
| assertUpdates(false) | ✅ | INSERT이므로 |
| ExponentialBackOffPolicy | ✅ | Worker별 데드락 시 간격 두고 재시도 |
| cleanupStep allowStartIfComplete | ✅ | DELETE는 멱등. 재시작 시에도 항상 실행 |
| CursorReader + Partitioning | ✅ | GROUP BY 1회 실행 유지 + 병렬 처리. ResultSet 공유 없음 (Worker별 독립 커넥션) |
| skip policy | 미적용 (의도적) | 집계 결과이므로 데이터 오류 가능성 낮음. 1건 에러 시 해당 파티션 전체 실패가 적절 |

---

## API 확장

### 현재 구조 (모두 Redis 조회)

```java
// RankingFacade — scope별 Redis prefix 분기
return switch (scope) {
    case "weekly" -> WEEKLY_ZSET_PREFIX;   // Redis
    case "monthly" -> MONTHLY_ZSET_PREFIX; // Redis
    default -> DAILY_ZSET_PREFIX;          // Redis
};
```

### 변경 후 구조 (weekly/monthly → MV 단일 소스)

```java
return switch (scope) {
    case "daily" -> getFromRedis(DAILY_ZSET_PREFIX, ...);
    case "weekly" -> getFromMv("weekly", ...);
    case "monthly" -> getFromMv("monthly", ...);
};
```

**MV 조회 흐름**:
1. 당일 period_key로 MV 테이블 조회
2. 당일 데이터 없으면 → 전일 period_key로 fallback (같은 공식, 1일 stale)
3. 전일도 없으면 → 빈 결과 반환
4. Product 상세 정보 조합 → 응답

**기존 API 시그니처 변경 없음**: `/api/v1/rankings?scope=weekly&date=20260416&size=20&page=0`

### 필요한 새 컴포넌트

| 레이어 | 파일 | 역할 |
|--------|------|------|
| domain | `MvProductRank.java` | MV 엔티티 (@Entity) |
| domain | `MvProductRankRepository.java` | Repository 인터페이스 |
| infrastructure | `MvProductRankJpaRepository.java` | JPA 구현체 |
| application | `RankingFacade.java` (수정) | MV 단일 소스 조회 + 전일 MV fallback |

---

## 실행 전략

### 스케줄링

| Job | 실행 시점 | 근거 |
|-----|----------|------|
| 주간 MV Job | **매일 01:00** | 전날까지의 7일 데이터 집계. RankingCorrectionJob(1시간 주기)과 시간 분리 |
| 월간 MV Job | **매일 01:30** | 전날까지의 30일 데이터 집계. 주간 Job 완료 후 실행 |

- 기존 23:50 carry-over 스케줄러와 시간 충돌 없음
- 매일 실행하여 슬라이딩 윈도우 유지

### 실행 명령

```bash
# 주간 랭킹
java -jar commerce-batch.jar --job.name=productRankingMvJob targetDate=20260416 scope=weekly

# 월간 랭킹
java -jar commerce-batch.jar --job.name=productRankingMvJob targetDate=20260416 scope=monthly
```

---

## 파일 구조

```
apps/commerce-batch/src/main/java/com/loopers/batch/job/rankingmv/
  ├── ProductRankingMvJobConfig.java        ← Job(3 Step) + Partitioner + Reader + Writer
  └── step/
      └── CleanupTasklet.java              ← Step 1: DELETE MV + staging + 3일 이전 정리

apps/commerce-api/src/main/java/com/loopers/
  ├── domain/ranking/
  │   ├── MvProductRank.java               ← MV 엔티티
  │   └── MvProductRankRepository.java     ← Repository 인터페이스
  ├── infrastructure/ranking/
  │   └── MvProductRankJpaRepository.java  ← JPA 구현체
  └── application/ranking/
      └── RankingFacade.java               ← (수정) MV 단일 소스 + 전일 fallback

apps/commerce-batch/src/test/resources/
  └── schema-batch-test.sql                ← DDL (MV + staging 포함)
```

---

## 구현 순서

### Phase 0: 설계 (완료)

- ✅ 0-1. 아키텍처 결정 — MV 단일 소스 (Redis fallback 없음, 전일 MV fallback)
- ✅ 0-2. MV 스키마 설계 — DDL 확정 (MV weekly/monthly + staging)
- ✅ 0-3. Job 설계 — Partitioning + Map-Reduce (3 Step)
- ✅ 0-4. Score 전략 — 방식 A (균등 합산, 전체 재계산), Reader SQL에서 LOG10 계산
- ✅ 0-5. 시간 윈도우 — 슬라이딩 윈도우 (매일 갱신)
- ✅ 0-6. 운영 기능 — faultTolerant + retry + ExponentialBackOffPolicy
- ✅ 0-7. 멱등성 — cleanup(DELETE) → 전체 재실행. RunIdIncrementer로 재실행 허용
- ✅ 0-8. 설계 문서 작성

### Phase 1: 배치 Job 구현 → R1, R2 충족

| # | 작업 | 상태 | 산출물 |
|---|------|------|--------|
| 1-1 | DDL 작성 | ✅ | `schema-batch-test.sql`에 MV weekly/monthly + staging 추가 |
| 1-2 | CleanupTasklet | ✅ | 당일 MV + staging DELETE + 3일 이전 정리 |
| 1-3 | ProductRankingMvJobConfig | ✅ | 3-Step Job (cleanup → partitioned aggregate → merge) |
| 1-4 | 컴파일 확인 | ✅ | BUILD SUCCESSFUL |

### Phase 2: API 확장 → R3 충족

| # | 작업 | 상태 | 산출물 |
|---|------|------|--------|
| 2-1 | MV 엔티티/리포지토리 | ✅ | `MvProductRank` (MappedSuperclass) + Weekly/Monthly 엔티티 + Repository + JPA 구현체 |
| 2-2 | RankingFacade 수정 | ✅ | daily→Redis, weekly/monthly→MV 단일 소스 + 전일 MV fallback |

### Phase 3: 테스트

| # | 작업 | 상태 | 산출물 |
|---|------|------|--------|
| 3-1 | Job 통합 테스트 | ✅ 코드 작성 | `ProductRankingMvJobE2ETest` — 시드 → Job → MV 결과 검증 |
| 3-2 | 멱등성 테스트 | ✅ 코드 작성 | 같은 파라미터 2회 실행 → MV 결과 동일 |
| 3-3 | 엣지 케이스 | ✅ 코드 작성 | 데이터 없음, 7일 미만, 100개 미만, 취소 반영 |
| 3-4 | 테스트 실행 | ⏳ 보류 | 메모리 부족으로 실행 보류. 아래 실행 가이드 참조 |
| 3-5 | API 통합 테스트 | | MV 조회 + 전일 fallback 동작 검증 (Phase 4에서 수동 검증 가능) |

**테스트 실행 가이드**:

```bash
# 사전 조건: Docker 실행 중 (Testcontainers가 MySQL + Redis 컨테이너를 자동 생성)
# JVM 메모리: 최소 1GB 여유 필요

# 전체 MV Job 테스트
./gradlew :apps:commerce-batch:test --tests "com.loopers.job.rankingmv.ProductRankingMvJobE2ETest"

# 개별 테스트 (메모리 절약)
./gradlew :apps:commerce-batch:test --tests "com.loopers.job.rankingmv.ProductRankingMvJobE2ETest\$WeeklyJob\$success"
```

테스트가 실패하면 확인할 것:
- `schema-batch-test.sql`에 product_metrics, MV, staging DDL이 있는지
- product 테이블에 `category_id` 컬럼이 있는지 (이번에 추가함)
- Testcontainers Docker 접근 가능한지

### Phase 4: 시나리오 검증 & 모니터링

| # | 작업 | 상태 | 산출물 |
|---|------|------|--------|
| 4-1 | 정상 실행 시나리오 | | 시드 데이터 기반 주간/월간 Job 실행 결과 |
| 4-2 | MV vs Redis 비교 | | 같은 기간 TOP 20 대조, score 차이 분석 |
| 4-3 | 성능 측정 | | Job 실행 시간, 처리 건수, Partitioning 효과 |

**시나리오 검증 절차**:

```bash
# 1. 인프라 기동
docker-compose -f docker/infra-compose.yml up -d

# 2. commerce-api 실행
./gradlew :apps:commerce-api:bootRun

# 3. 시드 데이터 생성
./scripts/seed-test-data.sh

# 4. MV 배치 실행 (별도 터미널)
./gradlew :apps:commerce-batch:bootRun --args="--job.name=productRankingMvJob targetDate=20260416 scope=weekly"
./gradlew :apps:commerce-batch:bootRun --args="--job.name=productRankingMvJob targetDate=20260416 scope=monthly"

# 5. API 검증
curl "http://localhost:8080/api/v1/rankings?scope=weekly&date=20260416&size=20"
curl "http://localhost:8080/api/v1/rankings?scope=monthly&date=20260416&size=20"
curl "http://localhost:8080/api/v1/rankings?scope=daily&size=20"  # 기존 Redis 경로

# 6. MV vs Redis 비교 (MySQL 직접 조회)
mysql -u root -p loopers -e "SELECT product_id, ranking, score FROM mv_product_rank_weekly WHERE period_key='20260416' ORDER BY ranking LIMIT 20;"

# 7. 멱등성 검증: 같은 명령 2회 실행 후 MV 건수 확인
mysql -u root -p loopers -e "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key='20260416';"

# 8. 전일 fallback 검증: 존재하지 않는 날짜로 조회
curl "http://localhost:8080/api/v1/rankings?scope=weekly&date=20260417&size=20"
# → 20260417 데이터 없으면 20260416 데이터가 반환되어야 함
```

### Phase 5: 문서 & PR → R4 충족

| # | 작업 | 상태 | 산출물 |
|---|------|------|--------|
| 5-1 | 설계 문서 갱신 | | 구현 결과, 성능 수치, 트레이드오프 반영 |
| 5-2 | PR 작성 | | 변경 요약 + 리뷰 포인트 2~3개 |
| 5-3 | 블로그 + 10주 회고 | | TL;DR 포함, 설계 판단 중심 |

**PR 리뷰 포인트 후보**:

1. **Partitioning + CursorReader 조합**: GROUP BY 집계에서 PagingReader 대신 Partitioning을 선택한 이유. CursorReader의 멀티스레드 한계를 어떻게 극복했는가?
2. **MV 단일 소스 원칙**: Redis fallback을 제거하고 전일 MV fallback으로 대체한 판단. 다른 공식의 결과를 같은 API의 fallback으로 쓰면 왜 안 되는가?
3. **전체 재계산 vs 증분 계산**: Late-Arriving Fact(지연 취소)로 인해 증분이 부적합한 이유. 성능 차이(10초 vs 3초)가 1일 1회 배치에서 의미 없는 이유는?

**블로그 구조 가이드** (소재 문서 `10-technical-writing-topics.md` 기반):

```
TL;DR: (1줄 요약)

1. 도입 — "Redis에 이미 랭킹이 있는데 왜 MV를 만드는가?"
   → 소재 4 (Lambda Architecture)

2. Score 설계 — 균등 합산 vs 지수 감쇠
   → 소재 1 + 전시 기간 편향 분석

3. Chunk vs Tasklet — 언제 무엇을 쓰는가
   → 소재 3 (Spring Batch 운영 기능 5가지)

4. Reader 선택 — CursorReader + Partitioning
   → 소재 8, 9 (GROUP BY에서 Paging이 치명적인 이유)

5. 전체 재계산 vs 증분 — Late-Arriving Fact
   → 소재 12 (취소가 과거 데이터를 변경하는 문제)

6. 데이터 소스 설계 — 단일 소스 원칙
   → 소재 4 하단 (Redis fallback 제거 판단)

7. 마무리 — 10주 회고
```
