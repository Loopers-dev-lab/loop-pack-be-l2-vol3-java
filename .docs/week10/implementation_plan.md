# Week 10 — 주간/월간 랭킹 구현 계획

> 본 문서는 Week 10 주간/월간 랭킹 시스템의 **구현 가이드**다. 설계 결정의 "왜" 는 `/save-design-notes` 로 분리 저장된 design-notes 문서를 참조.
> 이 문서는 **무엇을 어떤 순서로 어디에 어떤 코드로** 만드는지에 집중. TDD Red → Green → Refactor 루프를 각 Step 에 적용.

## 목표 & 범위

### 산출물
- **테이블 2개**: `mv_product_rank_weekly`, `mv_product_rank_monthly`
- **Spring Batch Job 2개**: `weeklyRankingJob`, `monthlyRankingJob` (Reader/Processor/Writer 구조)
- **Scheduler + Listener**: `@Scheduled` 기반 매일 실행 + `JobExecutionListener` 로 Redis `latest_date` 동기화
- **API 엔드포인트 2개**: `GET /api/v1/rankings/weekly`, `GET /api/v1/rankings/monthly`
- **캐시 2종**: 메인 캐시(`rankings:{period}:{snapshot_date}:{page}:{size}`, 24h TTL), 메타 캐시(`rankings:{period}:latest_date`, 25h TTL)

### 이 문서에서 다루지 않는 것 (기술부채)
- Slack 실패 알림 설정 (logback-spring.xml 수정 — 후속)
- 오래된 snapshot cleanup 배치
- 2단계 daily 중간 테이블 도입 (성능 측정 후 판단)
- 다중 인스턴스 스케줄러 ShedLock

---

## 사전 준비 (Step 0)

### 0-1. MySQL URL 옵션 확인

`rewriteBatchedStatements=true` 가 `application.yml` 의 JDBC URL 에 포함되어 있는지 확인. 없으면 batch UPSERT 가 multi-row 로 묶이지 않음.

```yaml
# apps/commerce-batch/src/main/resources/application.yml
datasource:
  url: jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/loopers?rewriteBatchedStatements=true&useUnicode=true&characterEncoding=utf8
```

### 0-2. commerce-batch 에 Redis 의존 추가

```kotlin
// apps/commerce-batch/build.gradle.kts
dependencies {
    implementation(project(":modules:redis"))  // ← 추가
    // 기존 의존 유지
}
```

### 0-3. @EnableScheduling 추가

```java
// apps/commerce-batch/src/main/java/com/loopers/CommerceBatchApplication.java
@SpringBootApplication
@EnableScheduling  // ← 추가
@EnableBatchProcessing
public class CommerceBatchApplication { ... }
```

### 0-4. `spring.batch.job.enabled=false` 확인

스케줄러만 돌게 해야 하므로 데몬 모드로 뜰 때 Job 자동 실행 금지.

```yaml
# apps/commerce-batch/src/main/resources/application.yml
spring:
  batch:
    job:
      enabled: false  # ← 반드시 false — 스케줄러가 trigger
```

---

## 구현 순서 (Step 1 ~ 7)

각 Step 은 **독립 PR 또는 논리적 커밋 단위**로 분해 가능. 모든 Step 은 TDD 로 진행 (테스트 먼저 → 구현 → 리팩토링).

| Step | 제목 | 주요 산출물 | 의존 |
|---|---|---|---|
| 1 | DDL 마이그레이션 | 테이블 2개 생성 | — |
| 2 | commerce-batch 도메인 레이어 | 엔티티 + Repository 인터페이스 + JDBC UPSERT 구현체 | 1 |
| 3 | commerce-batch Job 구성 | Reader / Processor / Writer / Job / Step | 2 |
| 4 | Scheduler + Listener | RankingScheduler + RankingLatestDateCacheListener | 3 |
| 5 | commerce-api 도메인 레이어 | 엔티티 + Repository (읽기) | 1 |
| 6 | commerce-api Controller/Facade | Controller + Facade + Cache | 5 |
| 7 | E2E 통합 검증 | 수동 & 자동 시나리오 | 2~6 |

---

## Step 1. DDL — JPA 엔티티로 테이블 생성

> Flyway SQL 파일 대신 commerce-batch 의 JPA `@Entity` 를 이용해 `ddl-auto=create` 로 테이블을 자동 생성하는 방식을 채택.
> 이유: 별도 마이그레이션 파일 없이 엔티티 변경만으로 스키마가 동기화되므로 개발 사이클이 단순해짐.

### 1-1. 파일 경로 (이미 구현 완료)
- `apps/commerce-batch/src/main/java/com/loopers/domain/rank/MvProductRankWeekly.java`
- `apps/commerce-batch/src/main/java/com/loopers/domain/rank/MvProductRankMonthly.java`
- `apps/commerce-batch/src/main/java/com/loopers/domain/rank/RankingMetrics.java` ← `ranking_metrics` 테이블 미러 엔티티 (배치 테스트 환경에서 테이블 생성 목적)

### 1-2. 엔티티가 생성하는 DDL 구조

```
mv_product_rank_weekly / mv_product_rank_monthly
  id            BIGINT AUTO_INCREMENT PRIMARY KEY
  snapshot_date DATE NOT NULL
  product_id    BIGINT NOT NULL
  rank_position INT NOT NULL          ← 'rank' 는 MySQL 8.0 예약어(Window Function)라 rank_position 으로 명명
  score         DOUBLE NOT NULL
  view_count    BIGINT NOT NULL
  like_count    BIGINT NOT NULL
  order_revenue DECIMAL(18,2) NOT NULL
  created_at    DATETIME(6) NOT NULL
  UNIQUE KEY uk_snapshot_product (snapshot_date, product_id)
  INDEX idx_snapshot_rank (snapshot_date, rank_position)
```

### 1-3. 검증
- `test` 프로파일로 부팅 → `ddl-auto=create` 가 적용돼 테이블 자동 생성
- MySQL 접속해 `DESC mv_product_rank_weekly` 로 구조 확인
- UNIQUE KEY / INDEX 존재 확인: `SHOW INDEX FROM mv_product_rank_weekly`

---

## Step 2. commerce-batch 도메인 레이어

### 2-1. 파일 경로
- `apps/commerce-batch/src/main/java/com/loopers/domain/rank/MvProductRankWeekly.java`
- `apps/commerce-batch/src/main/java/com/loopers/domain/rank/MvProductRankWeeklyRepository.java`
- `apps/commerce-batch/src/main/java/com/loopers/infrastructure/rank/MvProductRankWeeklyRepositoryImpl.java`
- monthly 동일 세트 (`MvProductRankMonthly`, `MvProductRankMonthlyRepository`, `MvProductRankMonthlyRepositoryImpl`)

### 2-2. Red — 테스트 먼저 작성

```java
// apps/commerce-batch/src/test/java/com/loopers/infrastructure/rank/MvProductRankWeeklyRepositoryImplIntegrationTest.java
@SpringBootTest
@ActiveProfiles("test")
class MvProductRankWeeklyRepositoryImplIntegrationTest {

    @Autowired MvProductRankWeeklyRepository repository;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("upsertAll()")
    class UpsertAll {

        @Test
        @DisplayName("최초 호출 시 행이 INSERT 된다")
        void insertOnFirstCall() {
            // arrange
            List<MvProductRankWeekly> rows = List.of(
                new MvProductRankWeekly(LocalDate.of(2026, 4, 16), 1L, 1, 100.0, 10L, 5L, new BigDecimal("1000.00")),
                new MvProductRankWeekly(LocalDate.of(2026, 4, 16), 2L, 2, 90.0, 9L, 4L, new BigDecimal("900.00"))
            );

            // act
            repository.upsertAll(rows);

            // assert
            assertThat(repository.countBySnapshotDate(LocalDate.of(2026, 4, 16))).isEqualTo(2);
        }

        @Test
        @DisplayName("같은 (snapshot_date, product_id) 로 재호출 시 값이 UPDATE 된다 (멱등성)")
        void updateOnDuplicateKey() {
            // arrange
            LocalDate date = LocalDate.of(2026, 4, 16);
            repository.upsertAll(List.of(new MvProductRankWeekly(date, 1L, 1, 100.0, 10L, 5L, new BigDecimal("1000.00"))));
            MvProductRankWeekly updated = new MvProductRankWeekly(date, 1L, 1, 200.0, 20L, 10L, new BigDecimal("2000.00"));

            // act
            repository.upsertAll(List.of(updated));

            // assert
            MvProductRankWeekly row = repository.findBySnapshotDateAndProductId(date, 1L).orElseThrow();
            assertThat(row.getScore()).isEqualTo(200.0);
            assertThat(row.getViewCount()).isEqualTo(20L);
        }

        @Test
        @DisplayName("created_at 은 최초 INSERT 시각을 유지하고 UPDATE 시 갱신되지 않는다")
        void createdAtIsPreservedOnUpdate() throws InterruptedException {
            // arrange
            LocalDate date = LocalDate.of(2026, 4, 16);
            repository.upsertAll(List.of(new MvProductRankWeekly(date, 1L, 1, 100.0, 10L, 5L, new BigDecimal("1000.00"))));
            LocalDateTime firstCreatedAt = repository.findBySnapshotDateAndProductId(date, 1L).orElseThrow().getCreatedAt();
            Thread.sleep(10);

            // act
            repository.upsertAll(List.of(new MvProductRankWeekly(date, 1L, 1, 200.0, 20L, 10L, new BigDecimal("2000.00"))));

            // assert
            LocalDateTime afterUpdate = repository.findBySnapshotDateAndProductId(date, 1L).orElseThrow().getCreatedAt();
            assertThat(afterUpdate).isEqualTo(firstCreatedAt);
        }
    }
}
```

### 2-3. Green — 구현

**엔티티**:
```java
// domain/rank/MvProductRankWeekly.java
@Getter
public class MvProductRankWeekly {
    private Long id;
    private final LocalDate snapshotDate;
    private final Long productId;
    private final int rank;
    private final double score;
    private final long viewCount;
    private final long likeCount;
    private final BigDecimal orderRevenue;
    private LocalDateTime createdAt;

    public MvProductRankWeekly(LocalDate snapshotDate, Long productId, int rank, double score,
                               long viewCount, long likeCount, BigDecimal orderRevenue) {
        this.snapshotDate = snapshotDate;
        this.productId = productId;
        this.rank = rank;
        this.score = score;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.orderRevenue = orderRevenue;
    }
}
```

- **BaseEntity 미상속** — MV 는 파생 데이터라 updatedAt/deletedAt 불필요.
- JPA 엔티티가 아닌 순수 POJO — JDBC 로 다루므로 `@Entity` 붙이지 않음. (JPA 로 읽기 엔티티를 정의할지는 테스트 용이성만을 위해 필요 시 별도 검토.)

**Repository 인터페이스**:
```java
// domain/rank/MvProductRankWeeklyRepository.java
public interface MvProductRankWeeklyRepository {
    void upsertAll(List<MvProductRankWeekly> rows);
    long countBySnapshotDate(LocalDate snapshotDate);
    Optional<MvProductRankWeekly> findBySnapshotDateAndProductId(LocalDate snapshotDate, Long productId);
}
```

**JDBC 구현체**:
```java
// infrastructure/rank/MvProductRankWeeklyRepositoryImpl.java
@Repository
@RequiredArgsConstructor
public class MvProductRankWeeklyRepositoryImpl implements MvProductRankWeeklyRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String UPSERT_SQL = """
        INSERT INTO mv_product_rank_weekly
            (snapshot_date, product_id, rank, score, view_count, like_count, order_revenue, created_at)
        VALUES
            (:snapshotDate, :productId, :rank, :score, :viewCount, :likeCount, :orderRevenue, NOW(6))
        ON DUPLICATE KEY UPDATE
            rank = VALUES(rank),
            score = VALUES(score),
            view_count = VALUES(view_count),
            like_count = VALUES(like_count),
            order_revenue = VALUES(order_revenue)
        """;

    @Override
    public void upsertAll(List<MvProductRankWeekly> rows) {
        SqlParameterSource[] params = rows.stream()
            .map(r -> new MapSqlParameterSource()
                .addValue("snapshotDate", r.getSnapshotDate())
                .addValue("productId", r.getProductId())
                .addValue("rank", r.getRank())
                .addValue("score", r.getScore())
                .addValue("viewCount", r.getViewCount())
                .addValue("likeCount", r.getLikeCount())
                .addValue("orderRevenue", r.getOrderRevenue()))
            .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(UPSERT_SQL, params);
    }

    @Override
    public long countBySnapshotDate(LocalDate snapshotDate) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE snapshot_date = :snapshotDate",
            Map.of("snapshotDate", snapshotDate),
            Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public Optional<MvProductRankWeekly> findBySnapshotDateAndProductId(LocalDate snapshotDate, Long productId) {
        List<MvProductRankWeekly> results = jdbc.query(
            "SELECT * FROM mv_product_rank_weekly WHERE snapshot_date = :snapshotDate AND product_id = :productId",
            Map.of("snapshotDate", snapshotDate, "productId", productId),
            (rs, rowNum) -> {
                MvProductRankWeekly row = new MvProductRankWeekly(
                    rs.getDate("snapshot_date").toLocalDate(),
                    rs.getLong("product_id"),
                    rs.getInt("rank"),
                    rs.getDouble("score"),
                    rs.getLong("view_count"),
                    rs.getLong("like_count"),
                    rs.getBigDecimal("order_revenue"));
                row.id = rs.getLong("id");
                row.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                return row;
            });
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
```

### 2-4. Refactor 포인트
- Monthly 도 동일 구조라 **공통 UPSERT SQL 템플릿 추출 가능** — 단, 테이블명만 다르므로 과한 추상화 지양. 두 클래스로 분리 유지가 단순성 승리.
- 세 필드만 다르게 받아 같은 SQL 을 생성하는 헬퍼는 네이티브 SQL 가독성을 해치므로 금지.

### 2-5. 체크
- [ ] Red 테스트 3개 모두 실패 확인
- [ ] Green 구현 후 모두 통과
- [ ] `created_at` 이 `NOW(6)` 로 초기화되고 `ON DUPLICATE KEY UPDATE` 절에 없음
- [ ] Monthly 세트도 동일 테스트 통과

---

## Step 3. commerce-batch Job 구성

### 3-1. 파일 경로
- `apps/commerce-batch/src/main/java/com/loopers/batch/ranking/weekly/WeeklyRankingJobConfig.java`
- `apps/commerce-batch/src/main/java/com/loopers/batch/ranking/weekly/WeeklyRankReader.java`
- `apps/commerce-batch/src/main/java/com/loopers/batch/ranking/weekly/WeeklyRankProcessor.java`
- `apps/commerce-batch/src/main/java/com/loopers/batch/ranking/weekly/WeeklyRankWriter.java`
- `apps/commerce-batch/src/main/java/com/loopers/batch/ranking/RankingJobTrigger.java`
- `apps/commerce-batch/src/main/java/com/loopers/batch/ranking/RankingAggregateRow.java` (Reader 결과 DTO)
- monthly 세트 (`MonthlyRankingJobConfig`, `MonthlyRankReader`, `MonthlyRankProcessor`, `MonthlyRankWriter`)

### 3-2. Red — 테스트

```java
// apps/commerce-batch/src/test/java/com/loopers/batch/ranking/weekly/WeeklyRankingJobIntegrationTest.java
@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
class WeeklyRankingJobIntegrationTest {

    @Autowired JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired MvProductRankWeeklyRepository repository;
    @Autowired RankingMetricsTestFixture metricsFixture;   // 테스트용 헬퍼 (아래 참고)
    @Autowired DatabaseCleanUp databaseCleanUp;

    private static final LocalDate SNAPSHOT = LocalDate.of(2026, 4, 16);

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("Rolling Window [today-7, today-1] 범위만 집계에 포함된다")
    void rollingWindowBoundary() throws Exception {
        // arrange: today-8 과 today 데이터 삽입 (제외 대상)
        metricsFixture.insertMetrics(SNAPSHOT.minusDays(8), 1L, 100, 10, 1000);
        metricsFixture.insertMetrics(SNAPSHOT,              1L, 100, 10, 1000);
        // 포함 대상: today-7 ~ today-1
        for (int d = 1; d <= 7; d++) {
            metricsFixture.insertMetrics(SNAPSHOT.minusDays(d), 1L, 10, 1, 100);
        }

        // act
        JobExecution exec = jobLauncherTestUtils.launchJob(jobParams(SNAPSHOT));

        // assert
        assertThat(exec.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        MvProductRankWeekly row = repository.findBySnapshotDateAndProductId(SNAPSHOT, 1L).orElseThrow();
        // 7일 × (view=10, like=1, order=100) 만 집계되어야 함
        assertThat(row.getViewCount()).isEqualTo(70L);
        assertThat(row.getLikeCount()).isEqualTo(7L);
        assertThat(row.getOrderRevenue()).isEqualByComparingTo("700.00");
    }

    @Test
    @DisplayName("TOP 100 만 적재한다 (101위 이하는 제외)")
    void top100Only() throws Exception {
        // arrange: 150 개 상품의 7일치 메트릭 삽입
        for (long pid = 1; pid <= 150; pid++) {
            for (int d = 1; d <= 7; d++) {
                metricsFixture.insertMetrics(SNAPSHOT.minusDays(d), pid, (int) pid, 0, 0);  // view_count 만 product_id 비례
            }
        }

        // act
        jobLauncherTestUtils.launchJob(jobParams(SNAPSHOT));

        // assert
        assertThat(repository.countBySnapshotDate(SNAPSHOT)).isEqualTo(100);
    }

    @Test
    @DisplayName("같은 snapshotDate 로 재실행하면 JobInstanceAlreadyCompleteException")
    void idempotentReRun() throws Exception {
        // arrange
        metricsFixture.insertMetrics(SNAPSHOT.minusDays(1), 1L, 10, 0, 0);
        jobLauncherTestUtils.launchJob(jobParams(SNAPSHOT));

        // act & assert
        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(jobParams(SNAPSHOT)))
            .isInstanceOf(JobInstanceAlreadyCompleteException.class);
    }

    @Test
    @DisplayName("trigger + run.id 조합으로 재실행 가능하다 (새 JobInstance)")
    void manualReRun() throws Exception {
        // arrange
        metricsFixture.insertMetrics(SNAPSHOT.minusDays(1), 1L, 10, 0, 0);
        jobLauncherTestUtils.launchJob(jobParams(SNAPSHOT));

        JobParameters manual = new JobParametersBuilder()
            .addString("snapshotDate", SNAPSHOT.toString())
            .addString("trigger", "WEIGHT_CHANGE")
            .addString("run.id", LocalDateTime.now().toString())
            .toJobParameters();

        // act
        JobExecution exec = jobLauncherTestUtils.launchJob(manual);

        // assert
        assertThat(exec.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(repository.countBySnapshotDate(SNAPSHOT)).isEqualTo(1);
    }

    private JobParameters jobParams(LocalDate date) {
        return new JobParametersBuilder()
            .addString("snapshotDate", date.toString())
            .toJobParameters();
    }
}
```

### 3-3. Green — 구현

**Reader 결과 DTO**:
```java
// batch/ranking/RankingAggregateRow.java
public record RankingAggregateRow(
    long productId,
    long viewCount,
    long likeCount,
    BigDecimal orderRevenue
) {}
```

**JobTrigger enum**:
```java
// batch/ranking/RankingJobTrigger.java
public enum RankingJobTrigger {
    WEIGHT_CHANGE,
    DATA_FIX,
    MANUAL_RERUN
}
```

**Reader**:
```java
// batch/ranking/weekly/WeeklyRankReader.java
@Configuration
public class WeeklyRankReaderConfig {

    @Bean
    @StepScope
    public JdbcCursorItemReader<RankingAggregateRow> weeklyRankReader(
            DataSource dataSource,
            @Value("#{jobParameters['snapshotDate']}") String snapshotDateStr) {

        LocalDate snapshot = LocalDate.parse(snapshotDateStr);
        LocalDate windowStart = snapshot.minusDays(7);
        LocalDate windowEnd   = snapshot.minusDays(1);

        return new JdbcCursorItemReaderBuilder<RankingAggregateRow>()
            .name("weeklyRankReader")
            .dataSource(dataSource)
            .sql("""
                SELECT product_id,
                       SUM(view_count)    AS view_count,
                       SUM(like_count)    AS like_count,
                       SUM(order_revenue) AS order_revenue
                FROM ranking_metrics
                WHERE metrics_date BETWEEN ? AND ?
                GROUP BY product_id
                ORDER BY (SUM(view_count) * 0.1 + SUM(like_count) * 0.2 + SUM(order_revenue) * 0.00001) DESC
                LIMIT 100
                """)
            .preparedStatementSetter((ps) -> {
                ps.setDate(1, Date.valueOf(windowStart));
                ps.setDate(2, Date.valueOf(windowEnd));
            })
            .rowMapper((rs, rowNum) -> new RankingAggregateRow(
                rs.getLong("product_id"),
                rs.getLong("view_count"),
                rs.getLong("like_count"),
                rs.getBigDecimal("order_revenue")))
            .build();
    }
}
```

- **가중치는 상수** — 지금은 `0.1 / 0.2 / 0.00001` 하드코딩. 추후 가중치 변경 이슈 발생 시 환경변수화 (주제 7 의 `trigger=WEIGHT_CHANGE` 재실행 흐름).
- **Chunk 50** 로 100행을 2회에 걸쳐 처리.

**Processor**:
```java
// batch/ranking/weekly/WeeklyRankProcessor.java
@Component
@StepScope
public class WeeklyRankProcessor implements ItemProcessor<RankingAggregateRow, MvProductRankWeekly> {

    private final LocalDate snapshotDate;
    private final AtomicInteger rankCounter = new AtomicInteger(0);

    public WeeklyRankProcessor(@Value("#{jobParameters['snapshotDate']}") String snapshotDateStr) {
        this.snapshotDate = LocalDate.parse(snapshotDateStr);
    }

    @Override
    public MvProductRankWeekly process(RankingAggregateRow item) {
        int rank = rankCounter.incrementAndGet();
        double score = item.viewCount() * 0.1 + item.likeCount() * 0.2
                     + item.orderRevenue().doubleValue() * 0.00001;
        return new MvProductRankWeekly(
            snapshotDate, item.productId(), rank, score,
            item.viewCount(), item.likeCount(), item.orderRevenue());
    }
}
```

- Reader 가 이미 `ORDER BY score DESC` 로 내림차순 정렬된 TOP 100 을 흘려보내므로 rank 는 **수신 순서대로 부여**.
- `@StepScope` 필수 — 매 Job 실행마다 `rankCounter` 초기화 돼야 함.

**Writer**:
```java
// batch/ranking/weekly/WeeklyRankWriter.java
@Component
@RequiredArgsConstructor
public class WeeklyRankWriter implements ItemWriter<MvProductRankWeekly> {

    private final MvProductRankWeeklyRepository repository;

    @Override
    public void write(Chunk<? extends MvProductRankWeekly> chunk) {
        repository.upsertAll((List<MvProductRankWeekly>) chunk.getItems());
    }
}
```

**JobConfig**:
```java
// batch/ranking/weekly/WeeklyRankingJobConfig.java
@Configuration
@RequiredArgsConstructor
public class WeeklyRankingJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job weeklyRankingJob(Step weeklyRankingStep,
                                RankingLatestDateCacheListener cacheListener,
                                JobListener defaultJobListener) {
        return new JobBuilder("weeklyRankingJob", jobRepository)
            .start(weeklyRankingStep)
            .listener(defaultJobListener)
            .listener(cacheListener)      // Step 4 에서 추가
            .build();
    }

    @Bean
    public Step weeklyRankingStep(JdbcCursorItemReader<RankingAggregateRow> weeklyRankReader,
                                  WeeklyRankProcessor weeklyRankProcessor,
                                  WeeklyRankWriter weeklyRankWriter) {
        return new StepBuilder("weeklyRankingStep", jobRepository)
            .<RankingAggregateRow, MvProductRankWeekly>chunk(50, transactionManager)
            .reader(weeklyRankReader)
            .processor(weeklyRankProcessor)
            .writer(weeklyRankWriter)
            .build();
    }
}
```

**테스트용 픽스처** (`RankingMetricsTestFixture`):
```java
// apps/commerce-batch/src/test/java/com/loopers/fixture/RankingMetricsTestFixture.java
@Component
@RequiredArgsConstructor
public class RankingMetricsTestFixture {
    private final NamedParameterJdbcTemplate jdbc;

    public void insertMetrics(LocalDate date, long productId, long viewCount, long likeCount, long orderRevenue) {
        jdbc.update("""
            INSERT INTO ranking_metrics (product_id, metrics_date, metrics_hour, view_count, like_count, order_revenue)
            VALUES (:productId, :date, 0, :view, :like, :order)
            """, Map.of(
                "productId", productId,
                "date", date,
                "view", viewCount,
                "like", likeCount,
                "order", orderRevenue));
    }
}
```

### 3-4. Refactor 포인트
- Reader 의 가중치 상수는 **추후 `RankingScoreCalculator` 같은 별도 도메인 객체**로 빼낼 수 있지만, 현재는 Reader/Processor 두 곳에 중복 — YAGNI 로 두고 가중치 변경 이슈가 실제로 발생하면 그때 추출.
- Monthly 는 Window 만 `[today-30, today-1]` 로 바뀌고 구조 동일 — 복사+수정 먼저, 2번째 구현 후 추상화 여지 재검토.

### 3-5. 체크
- [ ] Rolling Window 경계 테스트 통과
- [ ] TOP 100 제한 테스트 통과
- [ ] JobInstance 재실행 차단 테스트 통과
- [ ] `trigger + run.id` 수동 재실행 테스트 통과
- [ ] Monthly 세트도 동일 테스트 통과

---

## Step 4. Scheduler + Listener

### 4-1. 파일 경로
- `apps/commerce-batch/src/main/java/com/loopers/batch/ranking/RankingScheduler.java`
- `apps/commerce-batch/src/main/java/com/loopers/batch/ranking/RankingLatestDateCacheListener.java`

### 4-2. Red — 테스트

```java
// apps/commerce-batch/src/test/java/com/loopers/batch/ranking/RankingLatestDateCacheListenerIntegrationTest.java
@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
class RankingLatestDateCacheListenerIntegrationTest {

    @Autowired JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired RankingMetricsTestFixture metricsFixture;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisTemplate.delete("rankings:weekly:latest_date");
        redisTemplate.delete("rankings:monthly:latest_date");
    }

    @Test
    @DisplayName("Job 성공 시 latest_date 캐시에 snapshotDate 가 put 된다")
    void cachePutOnSuccess() throws Exception {
        // arrange
        LocalDate snapshot = LocalDate.of(2026, 4, 16);
        metricsFixture.insertMetrics(snapshot.minusDays(1), 1L, 10, 0, 0);

        // act
        JobExecution exec = jobLauncherTestUtils.launchJob(
            new JobParametersBuilder().addString("snapshotDate", snapshot.toString()).toJobParameters());

        // assert
        assertThat(exec.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        String cached = redisTemplate.opsForValue().get("rankings:weekly:latest_date");
        assertThat(cached).isEqualTo("2026-04-16");

        Long ttl = redisTemplate.getExpire("rankings:weekly:latest_date", TimeUnit.HOURS);
        assertThat(ttl).isBetween(24L, 25L);  // 25h 근사
    }

    @Test
    @DisplayName("Job 실패 시 캐시는 갱신되지 않는다")
    void cacheNotUpdatedOnFailure() throws Exception {
        // arrange: 기존 캐시 값
        redisTemplate.opsForValue().set("rankings:weekly:latest_date", "2026-04-15");
        // Reader 가 예외를 던지도록 데이터 준비 (예: 잘못된 DB 상태)
        // — 실무 테스트에선 TestContainer 에서 테이블 drop 등의 방법 사용

        // act
        // ... (실패 유도)

        // assert
        String cached = redisTemplate.opsForValue().get("rankings:weekly:latest_date");
        assertThat(cached).isEqualTo("2026-04-15");  // 변경되지 않음
    }
}
```

> 실패 시나리오는 현실적으로 `MvProductRankWeeklyRepository` 를 `@MockBean` 으로 대체해 `doThrow()` 로 구성하는 게 단순. 위 테스트의 두 번째 케이스는 그 패턴을 따르도록 조정할 것.

### 4-3. Green — 구현

**Listener**:
```java
// batch/ranking/RankingLatestDateCacheListener.java
@Component
@RequiredArgsConstructor
@Slf4j
public class RankingLatestDateCacheListener implements JobExecutionListener {

    private final StringRedisTemplate redisTemplate;

    private static final Duration TTL = Duration.ofHours(25);

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
            return;
        }
        String jobName = jobExecution.getJobInstance().getJobName();
        String snapshotDate = jobExecution.getJobParameters().getString("snapshotDate");
        if (snapshotDate == null) return;

        String cacheKey = resolveCacheKey(jobName);
        if (cacheKey == null) return;

        redisTemplate.opsForValue().set(cacheKey, snapshotDate, TTL);
        log.info("[{}] latest_date 캐시 put: {} -> {}", jobName, cacheKey, snapshotDate);
    }

    private String resolveCacheKey(String jobName) {
        return switch (jobName) {
            case "weeklyRankingJob"  -> "rankings:weekly:latest_date";
            case "monthlyRankingJob" -> "rankings:monthly:latest_date";
            default -> null;
        };
    }
}
```

**Scheduler**:
```java
// batch/ranking/RankingScheduler.java
@Component
@RequiredArgsConstructor
@Slf4j
public class RankingScheduler {

    private final JobLauncher jobLauncher;
    private final Job weeklyRankingJob;
    private final Job monthlyRankingJob;

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void runWeekly() {
        run(weeklyRankingJob, "weeklyRankingJob");
    }

    @Scheduled(cron = "0 30 1 * * *", zone = "Asia/Seoul")
    public void runMonthly() {
        run(monthlyRankingJob, "monthlyRankingJob");
    }

    private void run(Job job, String jobName) {
        LocalDate snapshotDate = LocalDate.now(ZoneId.of("Asia/Seoul"));
        try {
            jobLauncher.run(job, new JobParametersBuilder()
                .addString("snapshotDate", snapshotDate.toString())
                .toJobParameters());
        } catch (JobInstanceAlreadyCompleteException e) {
            log.warn("[{}] snapshotDate={} 이미 완료된 JobInstance — 재실행 스킵", jobName, snapshotDate);
        } catch (Exception e) {
            log.error("[{}] 실행 실패 snapshotDate={}", jobName, snapshotDate, e);
            // Slack 알림은 기술부채로 별도 처리
        }
    }
}
```

### 4-4. 체크
- [ ] Job 성공 시 Redis 에 `latest_date` key 가 설정됨
- [ ] TTL 이 25시간 근사
- [ ] Job FAILED 시 캐시 값 유지
- [ ] Monthly 키도 weekly 와 독립적으로 설정됨
- [ ] Scheduler 가 매 분 호출되지 않고 지정 시각에만 트리거 (`application.yml` 에 `logging.level.org.springframework.scheduling=DEBUG` 로 확인)

---

## Step 5. commerce-api 도메인 레이어

### 5-1. 파일 경로
- `apps/commerce-api/src/main/java/com/loopers/domain/rank/WeeklyRank.java`
- `apps/commerce-api/src/main/java/com/loopers/domain/rank/WeeklyRankRepository.java`
- `apps/commerce-api/src/main/java/com/loopers/infrastructure/rank/WeeklyRankJpaRepository.java`
- `apps/commerce-api/src/main/java/com/loopers/infrastructure/rank/WeeklyRankRepositoryImpl.java`
- monthly 세트

### 5-2. Red — 테스트

```java
// apps/commerce-api/src/test/java/com/loopers/infrastructure/rank/WeeklyRankRepositoryImplIntegrationTest.java
@SpringBootTest
@ActiveProfiles("test")
class WeeklyRankRepositoryImplIntegrationTest {

    @Autowired WeeklyRankRepository repository;
    @Autowired WeeklyRankTestFixture fixture;   // test 전용 INSERT 헬퍼
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @Test
    @DisplayName("findLatestSnapshotDate 는 가장 큰 snapshot_date 를 반환한다")
    void findLatestSnapshotDate() {
        // arrange
        fixture.insert(LocalDate.of(2026, 4, 14), 1L, 1);
        fixture.insert(LocalDate.of(2026, 4, 16), 2L, 1);
        fixture.insert(LocalDate.of(2026, 4, 15), 3L, 1);

        // act
        Optional<LocalDate> latest = repository.findLatestSnapshotDate();

        // assert
        assertThat(latest).hasValue(LocalDate.of(2026, 4, 16));
    }

    @Test
    @DisplayName("findBySnapshotDateOrderByRankAsc 는 rank 오름차순 페이지를 반환한다")
    void findBySnapshotDate() {
        // arrange
        LocalDate date = LocalDate.of(2026, 4, 16);
        for (int rank = 1; rank <= 30; rank++) {
            fixture.insert(date, (long) rank, rank);
        }

        // act
        Page<WeeklyRank> page = repository.findBySnapshotDateOrderByRankAsc(date, PageRequest.of(1, 10));

        // assert
        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getContent().get(0).getRank()).isEqualTo(11);
        assertThat(page.getTotalElements()).isEqualTo(30);
    }
}
```

### 5-3. Green — 구현

**엔티티** (읽기 전용 관점):
```java
// domain/rank/WeeklyRank.java
@Entity
@Table(name = "mv_product_rank_weekly")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyRank {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int rank;

    @Column(nullable = false)
    private double score;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "order_revenue", nullable = false)
    private BigDecimal orderRevenue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

**Repository 인터페이스**:
```java
// domain/rank/WeeklyRankRepository.java
public interface WeeklyRankRepository {
    Optional<LocalDate> findLatestSnapshotDate();
    Page<WeeklyRank> findBySnapshotDateOrderByRankAsc(LocalDate snapshotDate, Pageable pageable);
}
```

**Spring Data JPA**:
```java
// infrastructure/rank/WeeklyRankJpaRepository.java
public interface WeeklyRankJpaRepository extends JpaRepository<WeeklyRank, Long> {

    @Query("SELECT MAX(w.snapshotDate) FROM WeeklyRank w")
    Optional<LocalDate> findLatestSnapshotDate();

    Page<WeeklyRank> findBySnapshotDateOrderByRankAsc(LocalDate snapshotDate, Pageable pageable);
}
```

**Adapter**:
```java
// infrastructure/rank/WeeklyRankRepositoryImpl.java
@Repository
@RequiredArgsConstructor
public class WeeklyRankRepositoryImpl implements WeeklyRankRepository {
    private final WeeklyRankJpaRepository jpa;

    @Override
    public Optional<LocalDate> findLatestSnapshotDate() {
        return jpa.findLatestSnapshotDate();
    }

    @Override
    public Page<WeeklyRank> findBySnapshotDateOrderByRankAsc(LocalDate snapshotDate, Pageable pageable) {
        return jpa.findBySnapshotDateOrderByRankAsc(snapshotDate, pageable);
    }
}
```

### 5-4. 체크
- [ ] `findLatestSnapshotDate` 테스트 통과
- [ ] `findBySnapshotDate` 페이지네이션 테스트 통과
- [ ] Monthly 세트 동일 패턴으로 통과

---

## Step 6. commerce-api Controller + Facade + Cache

### 6-1. 파일 경로
- `apps/commerce-api/src/main/java/com/loopers/interfaces/api/ranking/RankingV1Controller.java` (수정)
- `apps/commerce-api/src/main/java/com/loopers/interfaces/api/ranking/RankingV1ApiSpec.java` (수정)
- `apps/commerce-api/src/main/java/com/loopers/application/ranking/RankingFacade.java` (수정)

### 6-2. Red — 테스트 (Facade 레벨, E2E 는 Step 7)

```java
// apps/commerce-api/src/test/java/com/loopers/application/ranking/RankingFacadeWeeklyIntegrationTest.java
@SpringBootTest
@ActiveProfiles("test")
class RankingFacadeWeeklyIntegrationTest {

    @Autowired RankingFacade rankingFacade;
    @Autowired WeeklyRankTestFixture fixture;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private static final LocalDate SNAPSHOT = LocalDate.of(2026, 4, 16);

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisTemplate.getConnectionFactory().getConnection().flushDb();
    }

    @Test
    @DisplayName("date 미지정 + latest_date 캐시 hit → 해당 snapshot 반환")
    void dateOmitted_cacheHit() {
        // arrange
        redisTemplate.opsForValue().set("rankings:weekly:latest_date", SNAPSHOT.toString());
        fixture.insertWithProduct(SNAPSHOT, 1L, 1);  // product 조인 위해 실제 상품도 삽입

        // act
        RankingListResponse response = rankingFacade.findWeeklyRanking(null, 0, 20);

        // assert
        assertThat(response.rankings()).hasSize(1);
        assertThat(response.rankings().get(0).rank()).isEqualTo(1);
    }

    @Test
    @DisplayName("date 미지정 + latest_date 캐시 miss → DB MAX 쿼리 폴백 + 캐시 put")
    void dateOmitted_cacheMiss_dbFallback() {
        // arrange: 캐시 비어있음
        fixture.insertWithProduct(SNAPSHOT, 1L, 1);

        // act
        RankingListResponse response = rankingFacade.findWeeklyRanking(null, 0, 20);

        // assert
        assertThat(response.rankings()).hasSize(1);
        String cached = redisTemplate.opsForValue().get("rankings:weekly:latest_date");
        assertThat(cached).isEqualTo(SNAPSHOT.toString());
    }

    @Test
    @DisplayName("date 명시 → 해당 snapshot 반환, latest_date 캐시 조회 없음")
    void dateSpecified() {
        // arrange
        fixture.insertWithProduct(SNAPSHOT, 1L, 1);

        // act
        RankingListResponse response = rankingFacade.findWeeklyRanking(SNAPSHOT, 0, 20);

        // assert
        assertThat(response.rankings()).hasSize(1);
    }

    @Test
    @DisplayName("snapshot 없음 → 200 + 빈 리스트")
    void emptySnapshot() {
        // act
        RankingListResponse response = rankingFacade.findWeeklyRanking(SNAPSHOT, 0, 20);

        // assert
        assertThat(response.rankings()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("동일 쿼리 두 번 호출 → 두 번째는 메인 캐시 hit")
    void mainCacheHit() {
        // arrange
        fixture.insertWithProduct(SNAPSHOT, 1L, 1);

        // act
        rankingFacade.findWeeklyRanking(SNAPSHOT, 0, 20);  // 첫 호출 — cache put
        String cacheKey = "rankings:weekly:" + SNAPSHOT + ":0:20";

        // assert
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
    }
}
```

### 6-3. Green — 구현

**Facade**:
```java
// application/ranking/RankingFacade.java (관련 부분만)
public RankingListResponse findWeeklyRanking(LocalDate date, int page, int size) {
    LocalDate snapshot = resolveSnapshotDate(date, "rankings:weekly:latest_date",
        weeklyRankRepository::findLatestSnapshotDate);
    if (snapshot == null) {
        return RankingListResponse.empty(page, size);
    }

    String cacheKey = "rankings:weekly:%s:%d:%d".formatted(snapshot, page, size);
    return cacheAside(cacheKey, Duration.ofHours(24), () -> {
        Page<WeeklyRank> ranks = weeklyRankRepository.findBySnapshotDateOrderByRankAsc(
            snapshot, PageRequest.of(page, size));
        return toResponse(ranks);
    });
}

private LocalDate resolveSnapshotDate(LocalDate given, String latestKey, Supplier<Optional<LocalDate>> dbFallback) {
    if (given != null) return given;

    String cached = redisTemplate.opsForValue().get(latestKey);
    if (cached != null) return LocalDate.parse(cached);

    Optional<LocalDate> fromDb = dbFallback.get();
    fromDb.ifPresent(d -> redisTemplate.opsForValue().set(latestKey, d.toString(), Duration.ofHours(25)));
    return fromDb.orElse(null);
}

private RankingListResponse toResponse(Page<WeeklyRank> ranks) {
    // Product/Brand 조인해서 RankingItemResponse 조립
    List<Long> productIds = ranks.getContent().stream().map(WeeklyRank::getProductId).toList();
    Map<Long, Product> products = productService.findAllByIds(productIds).stream()
        .collect(Collectors.toMap(Product::getId, Function.identity()));
    // ... brandService 조인

    List<RankingItemResponse> items = ranks.getContent().stream()
        .map(r -> RankingItemResponse.from(r, products.get(r.getProductId()) /* , brand */))
        .toList();
    return new RankingListResponse(items, (int) ranks.getNumber(), (int) ranks.getSize(), ranks.getTotalElements());
}
```

**Controller**:
```java
// interfaces/api/ranking/RankingV1Controller.java (추가)
@GetMapping("/weekly")
public ApiResponse<RankingListResponse> getWeeklyRanking(
        @RequestParam(required = false) LocalDate date,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(rankingFacade.findWeeklyRanking(date, page, size));
}

@GetMapping("/monthly")
public ApiResponse<RankingListResponse> getMonthlyRanking(
        @RequestParam(required = false) LocalDate date,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(rankingFacade.findMonthlyRanking(date, page, size));
}
```

**ApiSpec** 업데이트도 동일 시그니처로. (기존 ApiSpec 인터페이스에 메서드 추가)

### 6-4. Refactor 포인트
- `resolveSnapshotDate` 는 weekly/monthly 공통 — 잘 추출됨.
- `toResponse` 내부의 Product/Brand 조인 로직이 기존 daily 구현과 중복되면 **private 공통 메서드로 추출**. 중복 정도에 따라 판단.
- `Supplier` 로 DB 폴백을 주입받는 패턴은 재사용성 높음 — 유지.

### 6-5. 체크
- [ ] Facade 통합 테스트 5개 통과
- [ ] latest_date 캐시 miss → DB 폴백 → 캐시 put 검증
- [ ] 메인 캐시 hit 검증
- [ ] 빈 snapshot 응답 검증
- [ ] Monthly 동일 패턴

---

## Step 7. E2E 통합 검증

### 7-1. 자동 E2E 테스트

```java
// apps/commerce-api/src/test/java/com/loopers/interfaces/api/ranking/RankingV1ApiWeeklyE2ETest.java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class RankingV1ApiWeeklyE2ETest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired WeeklyRankTestFixture fixture;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @Test
    @DisplayName("GET /api/v1/rankings/weekly?date=X → 200 + 응답 스키마 일치")
    void getWeeklyRanking_withDate() {
        // arrange
        LocalDate date = LocalDate.of(2026, 4, 16);
        fixture.insertWithProduct(date, 1L, 1);

        // act
        ResponseEntity<ApiResponse<RankingListResponse>> response = restTemplate.exchange(
            "/api/v1/rankings/weekly?date=" + date + "&page=0&size=20",
            HttpMethod.GET, null,
            new ParameterizedTypeReference<>() {});

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().rankings()).hasSize(1);
    }
}
```

### 7-2. 수동 검증 시나리오

```bash
# 1. 배치 수동 실행 (스케줄러 대기 없이)
./gradlew :apps:commerce-batch:bootRun \
  --args='--spring.batch.job.name=weeklyRankingJob --snapshotDate=2026-04-16'

# 2. 재실행 시도 → JobInstanceAlreadyCompleteException
./gradlew :apps:commerce-batch:bootRun \
  --args='--spring.batch.job.name=weeklyRankingJob --snapshotDate=2026-04-16'

# 3. 가중치 변경 재집계
./gradlew :apps:commerce-batch:bootRun \
  --args='--spring.batch.job.name=weeklyRankingJob \
          --snapshotDate=2026-04-16 \
          --trigger=WEIGHT_CHANGE \
          --run.id=2026-04-16T14:30:22'

# 4. API 호출
curl -s 'http://localhost:8080/api/v1/rankings/weekly' | jq
curl -s 'http://localhost:8080/api/v1/rankings/weekly?date=2026-04-16&page=0&size=20' | jq
curl -s 'http://localhost:8080/api/v1/rankings/monthly' | jq

# 5. Redis 상태 확인
redis-cli GET 'rankings:weekly:latest_date'
redis-cli TTL 'rankings:weekly:latest_date'        # 25h 근사
redis-cli KEYS 'rankings:weekly:*'
redis-cli TTL 'rankings:weekly:2026-04-16:0:20'    # 24h 근사

# 6. DB 상태 확인
mysql> SELECT snapshot_date, COUNT(*) FROM mv_product_rank_weekly GROUP BY snapshot_date;
mysql> SELECT * FROM BATCH_JOB_EXECUTION_PARAMS WHERE parameter_name='trigger';
```

### 7-3. 데몬 모드 스케줄러 검증

```bash
# commerce-batch 를 데몬 모드로 띄움 (spring.batch.job.enabled=false)
./gradlew :apps:commerce-batch:bootRun

# 로그에 @Scheduled 등록 메시지 확인
# 01:00 / 01:30 KST 에 weekly / monthly 자동 트리거 확인 (시간대 주의)
```

---

## 최종 검증 체크리스트 (Go/No-Go)

- [ ] **DDL**: 테이블 2개, UNIQUE + INDEX 생성됨
- [ ] **Batch Domain**: Repository UPSERT 테스트 3종 통과 (INSERT / UPDATE / created_at 보존)
- [ ] **Batch Job**: Rolling Window 경계 / TOP 100 제한 / JobInstance 멱등성 / trigger 재실행 테스트 통과
- [ ] **Scheduler**: cron KST 반영, 부팅 시 Job 자동 실행 안 됨 (`enabled=false`)
- [ ] **Listener**: Job 성공 시 Redis put / 실패 시 유지 / 키 네임스페이스 분리
- [ ] **API Domain**: `findLatestSnapshotDate` / 페이지네이션 테스트 통과
- [ ] **API Facade**: date 미지정 → 캐시 → DB 폴백, 메인 캐시 hit, 빈 snapshot 200 응답
- [ ] **E2E**: 4종 API 호출 + Redis/DB 상태 일치
- [ ] **기술부채 문서화**: Slack 알림 / cleanup 배치 / 2단계 daily SOT — 별도 이슈/TODO 로 등록

---

## 참고할 기존 코드 (패턴 복제용)

- `apps/commerce-streamer/src/main/java/com/loopers/infrastructure/ranking/RankingMetricsJpaRepository.java` — 네이티브 UPSERT 패턴
- `apps/commerce-batch/src/main/java/com/loopers/batch/demo/DemoJobConfig.java` — Job 설정 뼈대
- `apps/commerce-batch/src/main/java/com/loopers/batch/common/JobListener.java`, `StepMonitorListener.java` — 기존 Listener 부착 지점
- `apps/commerce-api/src/main/java/com/loopers/application/ranking/RankingFacade.java` — 기존 daily/hourly cache-aside 패턴
- `modules/redis/src/main/java/com/loopers/config/redis/RankingKeys.java` — 캐시 키 네이밍
- `supports/logging/**/logback-spring.xml` + Slack Appender — Slack 알림 기술부채 후속 작업 진입점

---

## 다음 단계 (본 Step 완료 후 기술부채)

- Slack 실패 알림 구성 (logback-spring.xml + ERROR 레벨)
- 오래된 snapshot cleanup 배치 (Step 추가 또는 별도 Job)
- 2단계 daily 중간 테이블 도입 성능 측정
- 다중 인스턴스 스케줄러 ShedLock 또는 외부 크론 전환
- 메인 캐시 cold-start pre-warming
- `RankingManualRunner` 에 `run.id` 추가 — 동일 trigger + snapshotDate 조합 재실행 지원 (현재는 `JobInstanceAlreadyCompleteException` 발생)

---

## 실제 구현과 계획의 차이점 (구현 완료 후 기록)

> 계획 수립 이후 실제 구현 과정에서 변경·추가된 사항을 기록. 각 항목에 "왜 바뀌었는지" 포함.

### Step 3 변경사항

#### 가중치 하드코딩 → DB 동적 조회 (`BatchRankingWeightRepository`)

**계획서 원안**:
```java
// Reader SQL 내 하드코딩
SUM(view_count) * 0.1 + SUM(like_count) * 0.2 + SUM(order_revenue) * 0.00001
```

**실제 구현**:
```java
// WeeklyRankReaderConfig — 실행 시점에 DB 조회
BigDecimal viewWeight  = weightRepository.findWeightByEventType("VIEW");
BigDecimal likeWeight  = weightRepository.findWeightByEventType("LIKE");
BigDecimal orderWeight = weightRepository.findWeightByEventType("ORDER");
String sql = buildSql(viewWeight, likeWeight, orderWeight);
```

**변경 이유**: 가중치는 비즈니스 요인(마케팅 전략, 데이터 분포 변화)에 따라 변경될 수 있는 값이다. SOT(Source of Truth)는 DB의 `ranking_weight` 테이블로 두고, 배치 실행 시점에 조회한다. Redis 에 캐시하지 않은 이유는 배치가 매일 1회만 실행되므로 **신속성보다 정확성이 우선**이기 때문 — Redis 캐시 TTL 불일치로 오래된 가중치가 사용될 위험보다 DB 직접 조회 1회 비용이 훨씬 작다.

---

#### 점수 공식에 LOG 변환 추가

**계획서 원안**: 선형 합산 (`view * w1 + like * w2 + revenue * w3`)

**실제 구현**:
```java
LOG(1 + SUM(view_count))    * viewWeight
+ LOG(1 + SUM(like_count))  * likeWeight
+ LOG(1 + SUM(order_revenue)) * orderWeight
```

**변경 이유**: 선형 합산은 특정 지표(예: 주문 매출)가 극단적으로 큰 상품이 랭킹을 독점하는 문제가 있다. `LOG(1 + x)` 변환으로 롱테일을 압축해 다양한 상품이 경쟁 가능한 랭킹을 만든다. `+1`은 0값일 때 `LOG(0) = -∞` 가 되는 것을 방지.

---

#### `RankingAggregateRow`에 `score` 필드 추가

**계획서 원안**: score는 Processor에서 계산
```java
record RankingAggregateRow(long productId, long viewCount, long likeCount, BigDecimal orderRevenue)
```

**실제 구현**: Reader SQL에서 score 미리 계산해 DTO에 포함
```java
record RankingAggregateRow(long productId, long viewCount, long likeCount, BigDecimal orderRevenue, double score)
```

**변경 이유**: Reader SQL이 `ORDER BY score DESC`로 정렬해야 하므로 어차피 DB에서 score를 계산한다. 이를 Processor에서 다시 계산하면 중복이다. score를 DTO에 포함시켜 Processor는 rank 부여만 담당 — 단일 책임이 명확해진다.

---

### Step 4 변경사항

#### `RankingLatestDateCacheListener`에 count 검증 추가

**계획서 원안**: Job `COMPLETED` 이면 즉시 캐시 put

**실제 구현**:
```java
long count = countByJobName(jobName, snapshotDate);
if (count == 0) {
    log.warn("... 적재 데이터 없음 — latest_date 캐시 갱신 스킵");
    return;
}
stringRedisTemplate.opsForValue().set(cacheKey, snapshotDateStr, TTL);
```

**변경 이유**: 집계 대상 상품이 없으면(신규 서비스 초기 등) Job은 `COMPLETED`이지만 실제 적재 데이터가 없다. 이 상태에서 `latest_date`를 갱신하면 API가 빈 응답을 반환하는 snapshot을 가리키게 된다. 이전 성공 snapshot을 유지하는 것이 더 안전하므로 count=0 시 갱신 스킵.

**추가된 의존성**: Listener가 `MvProductRankWeeklyRepository`, `MvProductRankMonthlyRepository`를 주입받아 count 조회. 계획서의 Listener는 이 의존성이 없었음.

---

### Step 3 추가사항

#### `RankingJobTrigger.SCHEDULED` 추가 및 `RankingManualRunner` 신설

**계획서 설계 노트 원안**: "SCHEDULED는 미정의 — 파라미터 비어있음 = 자동 실행"

**실제 구현**:
- `RankingScheduler`: `trigger=SCHEDULED` 명시적 추가
- `RankingManualRunner`: CLI 진입점 신설 — `--trigger=WEIGHT_CHANGE` 등 옵션으로 수동 실행

```bash
# 수동 재실행 예시
./gradlew :apps:commerce-batch:bootRun \
  --args='--spring.batch.job.name=weeklyRankingJob --trigger=WEIGHT_CHANGE --snapshotDate=2026-04-16'
```

**변경 이유**: trigger를 명시해 `BATCH_JOB_EXECUTION_PARAMS` 이력에서 "자동 실행 vs 수동 실행"을 구분 가능하게 함. `RankingManualRunner`는 `ApplicationRunner` 구현으로 부팅 직후 실행되며, `--trigger` 옵션이 없으면 아무것도 하지 않아 스케줄러 데몬 모드와 충돌하지 않음.

**현재 제약**: `RankingManualRunner`에 `run.id`가 없어 동일 `trigger + snapshotDate` 조합은 한 번만 실행 가능. 같은 trigger로 재실행이 필요하면 다른 trigger 값을 사용해야 함 (기술부채).

---

### Step 6 추가사항

#### Facade 캐시 처리에 `RankingCacheRepository` 추상화 적용

**계획서 원안**: `StringRedisTemplate` 직접 사용 + `cacheAside()` private 메서드

**실제 구현**: `RankingCacheRepository` 인터페이스 도입

```java
// application 레이어
Optional<RankingResult> cached = rankingCacheRepository.get(cacheKey);
if (cached.isPresent()) return cached.get();
// ...
rankingCacheRepository.save(cacheKey, result);
```

**변경 이유**: DIP 원칙 준수 — application 레이어(Facade)가 Redis 구현 세부(`StringRedisTemplate`)를 직접 알지 않도록 인터페이스로 추상화. 테스트에서 `RankingCacheRepository` Mock 교체가 쉬워지는 부수효과.

#### `toRankingResult()` 제네릭 공통화

`WeeklyRank`, `MonthlyRank` 두 타입의 응답 조립 로직을 제네릭 private 메서드로 공통화. 계획서의 `toResponse(Page<WeeklyRank>)` 패턴보다 중복이 줄었다.

```java
private <T> RankingResult toRankingResult(
        List<T> rows,
        Function<T, Integer> rankExtractor,
        Function<T, Long> productIdExtractor,
        Function<T, Double> scoreExtractor, ...)
```

---

### Step 3 추가사항 — `@ConditionalOnProperty`

**계획서에 없던 설정**. 각 Job Config 클래스에 `@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = "weeklyRankingJob", matchIfMissing = true)` 적용.

**이유**: `spring.batch.job.name` 으로 특정 Job만 로드해 수동 실행 시 불필요한 Bean 초기화를 줄임. `matchIfMissing = true` 로 Job 이름 미지정(데몬 모드)에서는 모든 Job이 로드되어 스케줄러가 둘 다 트리거 가능.