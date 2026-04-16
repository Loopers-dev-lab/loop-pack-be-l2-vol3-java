# Round 10 Design - Spring Batch 기반 주간/월간 랭킹

> 본 설계는 `00-requirements.md`를 100% 만족시키기 위한 설계 문서입니다.
> 핵심 요구사항은 `product_metrics` 일간 집계 데이터를 Spring Batch Chunk 방식으로 읽고, 주간/월간 TOP 100 Read Model을 적재한 뒤, 기존 Ranking API에서 일간/주간/월간 랭킹을 함께 제공하는 것입니다.

---

## 1. 설계 결론

### 1.1 요구사항 매핑

| 요구사항 | 설계 대응 |
|---|---|
| Spring Batch Job을 파라미터 기반으로 실행 | `WeeklyProductRankingJob`, `MonthlyProductRankingJob` + `targetDate`, `scorerType` JobParameters |
| 대상 테이블 `product_metrics`를 읽어 집계 | Reader가 `product_metrics`를 날짜 범위로 읽고 `GROUP BY product_id` + `SUM(...)` 수행 |
| Chunk-Oriented Processing | `JdbcCursorItemReader` + `ItemProcessor` + `JdbcBatchItemWriter`로 staging 테이블 적재 |
| `mv_product_rank_weekly` 주간 TOP 100 | `product_metrics` 주간 합산 결과를 `mv_product_rank_weekly`에 publish |
| `mv_product_rank_monthly` 월간 TOP 100 | `product_metrics` 월간 합산 결과를 `mv_product_rank_monthly`에 publish |
| Ranking API 기간별 제공 | `GET /api/v1/rankings?period={daily\|weekly\|monthly}&date=yyyyMMdd&size=20&page=1` |

### 1.2 중요한 설계 판단

1. **집계 원천은 `product_metrics`로 고정한다.**
   - 요구사항의 대상 테이블이 `product_metrics`이므로 `ranking_daily_counter`를 주간/월간 집계 원천으로 사용하지 않는다.
   - `ranking_daily_counter`는 Round 9 실시간 랭킹 재구축용 projection이고, Round 10의 명시 요구사항을 대체하지 않는다.

2. **`product_metrics`는 일간 grain이어야 한다.**
   - 주간/월간 집계를 만들려면 최소한 `(metric_date, product_id)` 단위의 일별 row가 필요하다.
   - 현재 구현의 `product_metrics`가 `product_id` 단일 PK 누적 스냅샷이라면, Round 10 구현 전에 아래 "2.1 Source Table 전제"처럼 일간 테이블로 확장해야 한다.

3. **점수 계산과 TOP 100 정렬은 같은 기준으로 수행한다.**
   - Reader SQL에서 score를 계산하고 `ROW_NUMBER()`로 순위를 부여한다.
   - Processor가 다시 다른 공식으로 점수를 계산하지 않는다. SQL 정렬 기준과 저장 점수가 달라지는 버그를 막기 위함이다.

4. **MV 교체는 staging 후 publish한다.**
   - 최종 MV를 먼저 DELETE한 뒤 Chunk Step이 실패하면 API가 빈 랭킹을 볼 수 있다.
   - Chunk Step은 staging 테이블에만 쓰고, 마지막 publish Tasklet에서 짧은 트랜잭션으로 기존 MV를 교체한다.

5. **주간/월간 Job은 매일 실행한다.**
   - `targetDate=yesterday` 기준으로 현재 주/현재 월의 month-to-date 랭킹을 갱신한다.
   - 매월 1일 실행도 같은 방식으로 전월 마지막 일자를 targetDate로 삼아 전월 랭킹을 최종 확정한다.

---

## 2. Source Data 설계

### 2.1 `product_metrics` Source Table 전제

Round 10의 주간/월간 집계는 "일간 집계정보"를 기반으로 하므로 `product_metrics`는 아래 grain을 가져야 한다.

```sql
CREATE TABLE product_metrics (
    metric_date   DATE NOT NULL,              -- 집계 기준일
    product_id    BIGINT NOT NULL,
    like_count    BIGINT NOT NULL DEFAULT 0,
    sales_count   BIGINT NOT NULL DEFAULT 0,  -- 기존 코드의 paid order 집계명
    view_count    BIGINT NOT NULL DEFAULT 0,
    version       BIGINT NOT NULL DEFAULT 0,
    updated_at    DATETIME(6) NOT NULL,

    PRIMARY KEY (metric_date, product_id),
    INDEX idx_product_metrics_product_date (product_id, metric_date)
);
```

**현재 누적 스냅샷 테이블과의 차이:**

- 현재 코드의 `ProductMetricsEntity`는 `product_id` 단일 PK 누적값 구조다.
- 이 구조만으로는 특정 주/월에 기여한 수치를 분리할 수 없으므로 주간/월간 재계산이 불가능하다.
- 따라서 구현 시 선택지는 둘 중 하나다.
  - `product_metrics`를 `(metric_date, product_id)` 복합 PK의 일간 테이블로 전환한다.
  - 누적 스냅샷이 필요하면 별도 `product_metrics_total`로 분리하고, Round 10 집계 원천은 일간 `product_metrics`로 둔다.

### 2.2 상품 정보 조인 기준

Batch는 `product_read_model`을 조인해 MV에 상품 정보를 denormalize한다.

```sql
JOIN product_read_model prm ON prm.id = pm.product_id
WHERE prm.deleted_at IS NULL
```

`product_read_model`에는 `brand_id`, `brand_name`, `name`, `price`, `deleted_at`이 이미 있으므로 `products`와 `brands`를 다시 조인하지 않는다.

---

## 3. Materialized View 설계

MySQL에는 실제 Materialized View 기능이 없으므로 별도 테이블을 Read Model로 사용한다.

### 3.1 `mv_product_rank_weekly`

```sql
CREATE TABLE mv_product_rank_weekly (
    id               BIGINT NOT NULL AUTO_INCREMENT,

    week_start_date  DATE NOT NULL,
    week_end_date    DATE NOT NULL,
    scorer_type      VARCHAR(30) NOT NULL DEFAULT 'SATURATION',

    rank_position    INT NOT NULL,
    score            DECIMAL(18, 8) NOT NULL,

    like_count       BIGINT NOT NULL,
    sales_count      BIGINT NOT NULL,
    view_count       BIGINT NOT NULL,

    product_id       BIGINT NOT NULL,
    product_name     VARCHAR(200) NOT NULL,
    brand_id         BIGINT NOT NULL,
    brand_name       VARCHAR(100),
    price            DECIMAL(12, 2) NOT NULL,

    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_weekly_period_product (week_start_date, scorer_type, product_id),
    UNIQUE KEY uk_weekly_period_rank (week_start_date, scorer_type, rank_position),
    INDEX idx_weekly_period_rank (week_start_date, scorer_type, rank_position)
);
```

**`week_start_date`를 조회 키로 쓰는 이유:**

- `2024-W01` 같은 문자열은 ISO week-year 경계에서 실수하기 쉽다.
- API가 입력 `date`로 해당 주의 월요일을 계산하고 `week_start_date = ?`로 조회하면 모호성이 없다.

### 3.2 `mv_product_rank_monthly`

```sql
CREATE TABLE mv_product_rank_monthly (
    id                BIGINT NOT NULL AUTO_INCREMENT,

    month_start_date  DATE NOT NULL,
    month_key         CHAR(7) NOT NULL,        -- 예: 2024-04
    scorer_type       VARCHAR(30) NOT NULL DEFAULT 'SATURATION',

    rank_position     INT NOT NULL,
    score             DECIMAL(18, 8) NOT NULL,

    like_count        BIGINT NOT NULL,
    sales_count       BIGINT NOT NULL,
    view_count        BIGINT NOT NULL,

    product_id        BIGINT NOT NULL,
    product_name      VARCHAR(200) NOT NULL,
    brand_id          BIGINT NOT NULL,
    brand_name        VARCHAR(100),
    price             DECIMAL(12, 2) NOT NULL,

    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_monthly_period_product (month_start_date, scorer_type, product_id),
    UNIQUE KEY uk_monthly_period_rank (month_start_date, scorer_type, rank_position),
    INDEX idx_monthly_period_rank (month_start_date, scorer_type, rank_position)
);
```

### 3.3 Staging Table

Chunk Step은 최종 MV가 아니라 staging 테이블에 쓴다.

```sql
CREATE TABLE stg_product_rank_weekly (
    job_execution_id BIGINT NOT NULL,

    week_start_date  DATE NOT NULL,
    week_end_date    DATE NOT NULL,
    scorer_type      VARCHAR(30) NOT NULL,
    rank_position    INT NOT NULL,
    score            DECIMAL(18, 8) NOT NULL,

    like_count       BIGINT NOT NULL,
    sales_count      BIGINT NOT NULL,
    view_count       BIGINT NOT NULL,
    product_id       BIGINT NOT NULL,
    product_name     VARCHAR(200) NOT NULL,
    brand_id         BIGINT NOT NULL,
    brand_name       VARCHAR(100),
    price            DECIMAL(12, 2) NOT NULL,

    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,

    PRIMARY KEY (job_execution_id, rank_position),
    INDEX idx_stg_weekly_job (job_execution_id),
    INDEX idx_stg_weekly_period (week_start_date, scorer_type)
);
```

`stg_product_rank_monthly`도 동일한 구조를 사용하되 `month_start_date`, `month_key` 컬럼을 가진다.

---

## 4. Spring Batch Job 설계

### 4.1 Job 구조

**`WeeklyProductRankingJob`** - 매일 새벽 실행, `targetDate`가 속한 주를 재계산

```text
WeeklyProductRankingJob (targetDate, scorerType)
 ├── Step 1. cleanupWeeklyStagingStep  [Tasklet]
 ├── Step 2. aggregateWeeklyStep       [Chunk: Reader -> Processor -> Writer(staging)]
 └── Step 3. publishWeeklyMvStep       [Tasklet, single transaction]
```

**`MonthlyProductRankingJob`** - 매일 새벽 실행, `targetDate`가 속한 월을 재계산

```text
MonthlyProductRankingJob (targetDate, scorerType)
 ├── Step 1. cleanupMonthlyStagingStep [Tasklet]
 ├── Step 2. aggregateMonthlyStep      [Chunk: Reader -> Processor -> Writer(staging)]
 └── Step 3. publishMonthlyMvStep      [Tasklet, single transaction]
```

cleanup Step은 같은 period/scorer의 오래된 staging row를 지운다. 동일 JobExecution 재시작 시에는 Spring Batch 기본 동작대로 완료된 cleanup Step을 건너뛰어야 한다. 그래야 publish Step 실패 후 재시작할 때 이미 적재된 staging row를 보존하고 publish만 재시도할 수 있다.

### 4.2 JobParameters

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `targetDate` | `String (yyyy-MM-dd)` | Y | 집계 기준일. 이 날짜가 속한 주/월을 계산한다. |
| `scorerType` | `String` | N | 기본값 `SATURATION`. SQL score expression과 일치해야 한다. |
| `run.id` | `Long` | N | 이미 `COMPLETED`된 동일 파라미터 Job을 운영자가 강제 재실행할 때만 사용한다. |

`LocalDate.now()`를 Job 내부에서 직접 사용하지 않는다. 동일 `targetDate` 재실행 시 항상 같은 기간을 다시 계산해야 하기 때문이다.
이번 Round 구현 범위에서는 `SATURATION`만 허용하고, 지원하지 않는 `scorerType`이 들어오면 Job을 fail-fast 처리한다.

### 4.3 기간 계산

```java
LocalDate target = LocalDate.parse(targetDate);

LocalDate weekStart = target.with(DayOfWeek.MONDAY);
LocalDate weekEnd = target.with(DayOfWeek.SUNDAY);

LocalDate monthStart = target.withDayOfMonth(1);
LocalDate monthEnd = target.withDayOfMonth(target.lengthOfMonth());
String monthKey = target.format(DateTimeFormatter.ofPattern("yyyy-MM"));
```

### 4.4 Reader SQL - 주간

기본 scorer는 기존 batch `RankingScorerConfig`의 `SATURATION` 공식과 동일하게 둔다.

```sql
WITH aggregated AS (
    SELECT
        pm.product_id,
        SUM(pm.view_count)  AS view_count,
        SUM(pm.like_count)  AS like_count,
        SUM(pm.sales_count) AS sales_count,
        prm.name            AS product_name,
        prm.brand_id        AS brand_id,
        prm.brand_name      AS brand_name,
        prm.price           AS price
    FROM product_metrics pm
    JOIN product_read_model prm ON prm.id = pm.product_id
    WHERE pm.metric_date BETWEEN ? AND ?
      AND prm.deleted_at IS NULL
    GROUP BY
        pm.product_id,
        prm.name,
        prm.brand_id,
        prm.brand_name,
        prm.price
),
scored AS (
    SELECT
        aggregated.*,
        (
            0.15 * CASE WHEN view_count <= 0 THEN 0 ELSE view_count / (view_count + 100.0) END
          + 0.35 * CASE WHEN like_count <= 0 THEN 0 ELSE like_count / (like_count + 10.0) END
          + 0.50 * CASE WHEN sales_count <= 0 THEN 0 ELSE sales_count / (sales_count + 3.0) END
        ) AS score
    FROM aggregated
),
ranked AS (
    SELECT
        scored.*,
        ROW_NUMBER() OVER (ORDER BY score DESC, product_id ASC) AS rank_position
    FROM scored
)
SELECT
    product_id,
    view_count,
    like_count,
    sales_count,
    product_name,
    brand_id,
    brand_name,
    price,
    score,
    rank_position
FROM ranked
WHERE rank_position <= 100
ORDER BY rank_position ASC
```

**핵심 포인트:**

- `sales_count`는 기존 코드에서 주문/판매량 집계로 쓰는 컬럼이며 scorer의 `order` 입력으로 사용한다.
- SQL에서 score를 계산하고 같은 score로 `ROW_NUMBER()`를 부여한다.
- 동점은 `product_id ASC`로 고정해 재실행 시 순위가 흔들리지 않게 한다.
- 월간 Reader는 `pm.metric_date BETWEEN ? AND ?`에 month start/end를 바인딩하는 점만 다르고 나머지는 동일하다.

### 4.5 Reader Bean 예시

```java
@Bean
@StepScope
public JdbcCursorItemReader<ProductRankAggregate> weeklyProductRankingReader(
    DataSource dataSource,
    @Value("#{jobParameters['targetDate']}") String targetDate
) {
    LocalDate target = LocalDate.parse(targetDate);
    LocalDate weekStart = target.with(DayOfWeek.MONDAY);
    LocalDate weekEnd = target.with(DayOfWeek.SUNDAY);

    return new JdbcCursorItemReaderBuilder<ProductRankAggregate>()
        .name("weeklyProductRankingReader")
        .dataSource(dataSource)
        .sql(WEEKLY_RANKING_SQL)
        .preparedStatementSetter(ps -> {
            ps.setDate(1, Date.valueOf(weekStart));
            ps.setDate(2, Date.valueOf(weekEnd));
        })
        .rowMapper((rs, rowNum) -> new ProductRankAggregate(
            rs.getLong("product_id"),
            rs.getLong("view_count"),
            rs.getLong("like_count"),
            rs.getLong("sales_count"),
            rs.getString("product_name"),
            rs.getLong("brand_id"),
            rs.getString("brand_name"),
            rs.getBigDecimal("price"),
            rs.getBigDecimal("score"),
            rs.getInt("rank_position")
        ))
        .build();
}
```

### 4.6 Processor

Processor는 상태를 가지지 않는다.

```java
@Component
@StepScope
public class WeeklyProductRankingProcessor
    implements ItemProcessor<ProductRankAggregate, StagingWeeklyProductRankRow> {

    @Override
    public StagingWeeklyProductRankRow process(ProductRankAggregate item) {
        return StagingWeeklyProductRankRow.from(item, weekStart, weekEnd, scorerType, jobExecutionId);
    }
}
```

`AtomicInteger`로 순위를 부여하지 않는다. Chunk 재시작, skip/retry, 병렬화 여부에 따라 순번이 깨질 수 있기 때문이다. 순위는 Reader SQL의 `ROW_NUMBER()` 결과를 그대로 사용한다.

### 4.7 Writer

```java
@Bean
public JdbcBatchItemWriter<StagingWeeklyProductRankRow> weeklyProductRankingWriter(DataSource dataSource) {
    return new JdbcBatchItemWriterBuilder<StagingWeeklyProductRankRow>()
        .dataSource(dataSource)
        .sql("""
            INSERT INTO stg_product_rank_weekly (
                job_execution_id,
                week_start_date, week_end_date, scorer_type,
                rank_position, score,
                like_count, sales_count, view_count,
                product_id, product_name, brand_id, brand_name, price,
                created_at, updated_at
            ) VALUES (
                :jobExecutionId,
                :weekStartDate, :weekEndDate, :scorerType,
                :rankPosition, :score,
                :likeCount, :salesCount, :viewCount,
                :productId, :productName, :brandId, :brandName, :price,
                :createdAt, :updatedAt
            )
            ON DUPLICATE KEY UPDATE
                week_start_date = VALUES(week_start_date),
                week_end_date = VALUES(week_end_date),
                scorer_type = VALUES(scorer_type),
                score = VALUES(score),
                like_count = VALUES(like_count),
                sales_count = VALUES(sales_count),
                view_count = VALUES(view_count),
                product_id = VALUES(product_id),
                product_name = VALUES(product_name),
                brand_id = VALUES(brand_id),
                brand_name = VALUES(brand_name),
                price = VALUES(price),
                updated_at = VALUES(updated_at)
            """)
        .beanMapped()
        .build();
}
```

Chunk size는 100으로 둔다. 최종 결과가 TOP 100이므로 한 청크 안에서 staging 적재가 끝난다. 그래도 Reader/Processor/Writer 구조를 유지해 Spring Batch의 실행 이력, 재시작, 처리 건수 집계를 활용한다.
Writer는 `ON DUPLICATE KEY UPDATE`를 사용해 같은 `job_execution_id + rank_position` 재쓰기에도 실패하지 않게 한다.

### 4.8 Publish Tasklet

Publish Step은 하나의 DB 트랜잭션으로 실행한다.

```sql
DELETE FROM mv_product_rank_weekly
WHERE week_start_date = ? AND scorer_type = ?;

INSERT INTO mv_product_rank_weekly (
    week_start_date, week_end_date, scorer_type,
    rank_position, score,
    like_count, sales_count, view_count,
    product_id, product_name, brand_id, brand_name, price,
    created_at, updated_at
)
SELECT
    week_start_date, week_end_date, scorer_type,
    rank_position, score,
    like_count, sales_count, view_count,
    product_id, product_name, brand_id, brand_name, price,
    created_at, updated_at
FROM stg_product_rank_weekly
WHERE job_execution_id = ?
ORDER BY rank_position ASC;

DELETE FROM stg_product_rank_weekly
WHERE job_execution_id = ?;
```

실패 시나리오:

| 실패 위치 | MV 영향 | 재실행 방식 |
|---|---|---|
| staging cleanup 실패 | 기존 MV 유지 | 동일 파라미터 재실행 |
| Chunk Step 실패 | 기존 MV 유지, staging만 불완전 | 동일 JobExecution 재시작 시 Writer upsert로 재쓰기 |
| publish Step 실패 | 트랜잭션 롤백으로 기존 MV 유지 | 동일 파라미터 재실행 시 publish 재시도 |

---

## 5. Ranking API 확장 설계

### 5.1 API 스펙

```http
GET /api/v1/rankings?period={daily|weekly|monthly}&date=yyyyMMdd&size=20&page=1
```

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---:|---|---|
| `period` | `String` | N | `daily` | `daily`, `weekly`, `monthly` |
| `date` | `String (yyyyMMdd)` | N | period별 기본값 | 기간 기준 날짜 |
| `page` | `int` | N | `1` | 요구사항 호환을 위해 1-based |
| `size` | `int` | N | `20` | 최대 100 |

**호환성 정책:**

- 기존 요청인 `GET /api/v1/rankings?date=yyyyMMdd&size=20&page=1`은 `period=daily`로 처리한다.
- `page=0`이 들어오면 `page=1`로 보정해 현재 구현의 0-based 요청도 깨지지 않게 한다.
- 내부 offset은 `(page - 1) * size`로 계산한다.

### 5.2 날짜 해석

| period | `date` 미지정 시 | 조회 키 |
|---|---|---|
| `daily` | 오늘 | Redis key `yyyyMMdd` |
| `weekly` | 어제 | `week_start_date` |
| `monthly` | 어제 | `month_start_date` |

주간/월간 기본값을 어제로 두는 이유는 `product_metrics` 일간 집계가 보통 하루가 닫힌 뒤 확정되기 때문이다. 명시적으로 오늘 날짜를 요청하면 오늘이 속한 기간의 최신 publish 결과를 조회한다.

### 5.3 Controller

```java
@GetMapping
public ResponseEntity<RankingPageResponse> getRankings(
    @RequestParam(required = false, defaultValue = "daily") String period,
    @RequestParam(required = false) String date,
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "20") int size
) {
    int safePage = Math.max(1, page);
    int safeSize = Math.max(1, Math.min(size, 100));

    RankingPageOutDto result = rankingQueryFacade.getRankings(
        RankingPeriod.from(period),
        date,
        safePage,
        safeSize
    );

    return ResponseEntity.ok(RankingPageResponse.from(result));
}
```

### 5.4 `RankingPeriod`

```java
public enum RankingPeriod {
    DAILY, WEEKLY, MONTHLY;

    public static RankingPeriod from(String value) {
        if (value == null || value.isBlank()) {
            return DAILY;
        }
        try {
            return RankingPeriod.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.INVALID_RANKING_PERIOD);
        }
    }
}
```

### 5.5 Facade 분기

```java
@Service
@RequiredArgsConstructor
public class RankingQueryFacade {

    private final RankingQueryService dailyRankingQueryService;
    private final WeeklyRankingQueryService weeklyRankingQueryService;
    private final MonthlyRankingQueryService monthlyRankingQueryService;

    @Transactional(readOnly = true)
    public RankingPageOutDto getRankings(RankingPeriod period, String dateStr, int page, int size) {
        return switch (period) {
            case DAILY -> dailyRankingQueryService.getRankings(dateStr, page, size);
            case WEEKLY -> weeklyRankingQueryService.getRankings(dateStr, page, size);
            case MONTHLY -> monthlyRankingQueryService.getRankings(dateStr, page, size);
        };
    }
}
```

Facade는 Service만 호출한다. Repository나 Port를 직접 호출하지 않는다.

### 5.6 주간/월간 Query Port

```java
public interface MvProductRankWeeklyQueryPort {
    RankingPageOutDto findByWeekStartDate(LocalDate weekStartDate, String scorerType, int page, int size);
}

public interface MvProductRankMonthlyQueryPort {
    RankingPageOutDto findByMonthStartDate(LocalDate monthStartDate, String scorerType, int page, int size);
}
```

Query 구현은 단일 MV 테이블만 조회한다.

```sql
SELECT
    rank_position,
    product_id,
    product_name,
    price,
    brand_name,
    score
FROM mv_product_rank_weekly
WHERE week_start_date = ?
  AND scorer_type = ?
ORDER BY rank_position ASC
LIMIT ? OFFSET ?;
```

`totalElements`는 해당 기간 MV row count로 계산한다. TOP 100만 저장하므로 최대값은 100이다.

---

## 6. 패키지 구조

### 6.1 `commerce-batch`

```text
apps/commerce-batch/src/main/java/com/loopers/batch/job/productranking/
├── weekly/
│   ├── WeeklyProductRankingJobConfig.java
│   ├── WeeklyProductRankingReaderConfig.java
│   ├── WeeklyProductRankingProcessor.java
│   ├── WeeklyProductRankingWriterConfig.java
│   ├── CleanupWeeklyStagingTasklet.java
│   ├── PublishWeeklyMvTasklet.java
│   └── dto/
│       ├── ProductRankAggregate.java
│       └── StagingWeeklyProductRankRow.java
├── monthly/
│   └── weekly와 동일 구조
└── scorer/
    └── RankingScoreSqlExpressions.java
```

`RankingScoreSqlExpressions`는 `SATURATION` scorer의 SQL 표현식을 한 곳에서 관리한다. Java scorer와 SQL expression이 달라지면 TOP 100 순위가 틀어지므로, scorer 추가 시 반드시 SQL expression도 함께 추가한다.

### 6.2 `commerce-api`

```text
apps/commerce-api/src/main/java/com/loopers/ranking/
├── application/
│   ├── facade/
│   │   └── RankingQueryFacade.java
│   ├── service/
│   │   ├── RankingQueryService.java
│   │   ├── WeeklyRankingQueryService.java
│   │   └── MonthlyRankingQueryService.java
│   └── port/out/
│       ├── MvProductRankWeeklyQueryPort.java
│       └── MvProductRankMonthlyQueryPort.java
├── domain/
│   └── model/
│       └── RankingPeriod.java
├── infrastructure/
│   ├── entity/
│   │   ├── MvProductRankWeeklyEntity.java
│   │   └── MvProductRankMonthlyEntity.java
│   └── query/
│       ├── MvProductRankWeeklyQueryAdapter.java
│       └── MvProductRankMonthlyQueryAdapter.java
└── interfaces/web/controller/
    └── RankingQueryController.java
```

---

## 7. 운영 전략

### 7.1 스케줄링

K8s CronJob 기준 예시:

```yaml
# 주간 랭킹: 매일 03:00, 어제가 속한 주를 재계산
schedule: "0 3 * * *"
command:
  - "/bin/sh"
  - "-c"
args:
  - >
    java -jar commerce-batch.jar
    --spring.batch.job.name=weeklyProductRankingJob
    --targetDate=$(date -d 'yesterday' +%Y-%m-%d)
    --scorerType=SATURATION

---

# 월간 랭킹: 매일 04:00, 어제가 속한 월을 재계산
schedule: "0 4 * * *"
command:
  - "/bin/sh"
  - "-c"
args:
  - >
    java -jar commerce-batch.jar
    --spring.batch.job.name=monthlyProductRankingJob
    --targetDate=$(date -d 'yesterday' +%Y-%m-%d)
    --scorerType=SATURATION
```

K8s CronJob을 선택하면 배치 실행 시점에 pod 1개만 띄우는 운영이 가능해 애플리케이션 다중 인스턴스의 중복 실행 문제를 줄일 수 있다.

### 7.2 재실행

| 상황 | 대응 |
|---|---|
| Job 실패 | 동일 `targetDate`, `scorerType`으로 재실행 |
| 이미 완료된 기간을 강제 재생성 | `run.id`를 추가해 새 JobInstance로 실행 |
| scorer 공식 변경 후 재계산 | `scorerType`을 명시하고 필요한 기간별로 재실행 |
| staging 찌꺼기 존재 | 다음 새 JobExecution의 cleanup Step이 동일 period + `scorerType` 기준으로 제거 |

### 7.3 모니터링

| 지표 | 기대값 / 용도 |
|---|---|
| `JobExecution.status` | `COMPLETED` 여부 |
| `StepExecution.readCount` | 최대 100 |
| `StepExecution.writeCount` | 최대 100 |
| publish 후 MV row count | 기간별 최대 100, staging read count와 일치 |
| 실패 알림 | 기존 Slack Appender 또는 Batch 실패 알림 |

---

## 8. 테스트 전략

| 테스트 | 검증 내용 |
|---|---|
| 기간 계산 단위 테스트 | `targetDate`에서 주 시작/끝, 월 시작/끝 계산 |
| Reader SQL 통합 테스트 | `product_metrics` 여러 날짜 row를 합산하고 TOP 100만 반환 |
| score 정렬 테스트 | SQL score와 기대 순위가 일치, 동점 시 `product_id ASC` |
| Job 통합 테스트 | staging 적재 후 publish로 MV 교체, 실패 시 기존 MV 유지 |
| API 테스트 | `period=daily/weekly/monthly`, `page=1`, `page=0` 보정, `size` 상한 |
| 회귀 테스트 | 기존 `GET /api/v1/rankings?date=yyyyMMdd&size=20&page=1`이 daily로 동작 |

---

## 9. 체크리스트 검증

| 요구사항 체크 | 설계 충족 여부 |
|---|---|
| Spring Batch Job을 작성하고 파라미터 기반으로 동작 | 충족: `targetDate`, `scorerType`, `run.id` |
| Chunk-Oriented Processing 기반 배치 처리 | 충족: Reader/Processor/Writer Chunk Step |
| Materialized View 구조 설계 및 올바른 적재 | 충족: weekly/monthly MV + staging publish |
| API가 일간/주간/월간 랭킹 제공 | 충족: `period` 파라미터와 Facade 분기 |
| 조회 형태별 적절한 데이터 기반 | 충족: daily=기존 Redis, weekly/monthly=MV |

---

## 10. 남은 구현 시 주의사항

- `product_metrics`가 일간 grain이 아니면 주간/월간 집계는 정확히 구현할 수 없다. 이 경우 Source Table 전환이 선행 작업이다.
- scorer를 `SATURATION` 외로 확장할 때는 Java scorer뿐 아니라 SQL score expression도 반드시 함께 추가한다.
- MV가 상품 정보를 denormalize하므로 상품명/가격/브랜드 변경은 다음 배치 전까지 stale할 수 있다. 요구 실시간성이 높아지면 상품 변경 이벤트로 MV를 보정하는 Listener를 추가한다.
- `product_metrics` 일간 집계 완료 시각보다 주간/월간 배치가 먼저 돌지 않도록 Cron 순서를 분리한다.
