package com.loopers.batch.job;

import com.loopers.batch.job.rank.WeeklyRankJobConfig;
import com.loopers.domain.rank.MvProductRankRepository;
import com.loopers.domain.rank.MvProductRankRow;
import com.loopers.domain.rank.RankPeriodType;
import com.loopers.ranking.ScoreCalculator;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        "spring.batch.job.name=" + WeeklyRankJobConfig.JOB_NAME,
        "spring.batch.job.enabled=false"
})
class FullPipelineE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(WeeklyRankJobConfig.JOB_NAME)
    private Job weeklyRankJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MvProductRankRepository mvProductRankRepository;

    @Autowired
    private ScoreCalculator rankScoreCalculator;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(weeklyRankJob);
        databaseCleanUp.truncateAllTables();
        cleanBatchMetaTables();
    }

    private void cleanBatchMetaTables() {
        jdbcTemplate.execute("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT");
        jdbcTemplate.execute("DELETE FROM BATCH_STEP_EXECUTION");
        jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT");
        jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION_PARAMS");
        jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION");
        jdbcTemplate.execute("DELETE FROM BATCH_JOB_INSTANCE");
    }

    @DisplayName("E2E: Phase 1(시뮬레이션) → Phase 2(weeklyRank) → MV 검증 — 10상품 × 7일")
    @Test
    void fullPipeline() throws Exception {
        // Phase 1 시뮬레이션: product_daily_signals → mv_product_score_daily 직접 적재
        LocalDate monday = LocalDate.of(2026, 4, 6);
        for (int day = 0; day < 7; day++) {
            LocalDate date = monday.plusDays(day);
            for (long productId = 1; productId <= 10; productId++) {
                long view = productId * 10;
                long like = productId * 5;
                BigDecimal order = BigDecimal.valueOf(productId * 100);
                double score = rankScoreCalculator.calculateTotal(view, like, order);
                insertScoreDaily(productId, date, score, view, like, order);
            }
        }

        // Phase 2: weeklyRankJob
        JobParameters params = new JobParametersBuilder()
                .addString("date", "20260408")
                .toJobParameters();
        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        // assert
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<MvProductRankRow> results = mvProductRankRepository.findByPeriodKey(
                RankPeriodType.WEEKLY, "2026W15", 0, 100);
        assertThat(results).hasSize(10);
        assertThat(results.get(0).rankNo()).isEqualTo(1);
        assertThat(results.get(0).refProductId()).isEqualTo(10L);
    }

    @DisplayName("알고리즘 변경 시뮬레이션: score 변경 후 Phase 2 재실행 → 랭킹 역전")
    @Test
    void algorithmChangeSimulation() throws Exception {
        // 초기 score: 상품1=100, 상품2=7000
        LocalDate date = LocalDate.of(2026, 4, 6);
        insertScoreDaily(1L, date, 100.0, 1000, 0, BigDecimal.ZERO);
        insertScoreDaily(2L, date, 7000.0, 0, 0, BigDecimal.valueOf(10000));

        // Phase 2 실행 → 상품2가 1위
        JobParameters params1 = new JobParametersBuilder()
                .addString("date", "20260408")
                .addLong("run.id", 1L)
                .toJobParameters();
        jobLauncherTestUtils.launchJob(params1);

        List<MvProductRankRow> before = mvProductRankRepository.findByPeriodKey(
                RankPeriodType.WEEKLY, "2026W15", 0, 10);
        assertThat(before.get(0).refProductId()).isEqualTo(2L);

        // 알고리즘 변경 시뮬레이션: score 직접 UPDATE (Phase 1 재실행 효과)
        jdbcTemplate.update("UPDATE mv_product_score_daily SET score = 900.0 WHERE product_db_id = 1");
        jdbcTemplate.update("UPDATE mv_product_score_daily SET score = 500.0 WHERE product_db_id = 2");

        // Phase 2 재실행 → 상품1이 1위 (SQL 변경 없이!)
        cleanBatchMetaTables();
        JobParameters params2 = new JobParametersBuilder()
                .addString("date", "20260408")
                .addLong("run.id", 2L)
                .toJobParameters();
        jobLauncherTestUtils.launchJob(params2);

        List<MvProductRankRow> after = mvProductRankRepository.findByPeriodKey(
                RankPeriodType.WEEKLY, "2026W15", 0, 10);
        assertThat(after.get(0).refProductId()).isEqualTo(1L);
        assertThat(after.get(0).score()).isCloseTo(900.0, within(0.01));
    }

    @DisplayName("멱등성: weeklyRankJob 2회 실행 → 결과 동일")
    @Test
    void weeklyRankIdempotency() throws Exception {
        LocalDate date = LocalDate.of(2026, 4, 6);
        insertScoreDaily(1L, date, 720.0, 100, 50, BigDecimal.valueOf(1000));

        for (int run = 1; run <= 2; run++) {
            cleanBatchMetaTables();
            JobParameters params = new JobParametersBuilder()
                    .addString("date", "20260408")
                    .addLong("run.id", (long) run)
                    .toJobParameters();
            JobExecution execution = jobLauncherTestUtils.launchJob(params);
            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        }

        assertThat(mvProductRankRepository.countByPeriodKey(RankPeriodType.WEEKLY, "2026W15"))
                .as("S2: 2회 실행 시 version=1/2 공존 (cleanup은 별도)").isEqualTo(2);
    }

    @DisplayName("DailyScoreJob 미실행 → MV 빈 상태, COMPLETED")
    @Test
    void weeklyRankWithoutDailyScore() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("date", "20260408")
                .toJobParameters();
        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(mvProductRankRepository.countByPeriodKey(RankPeriodType.WEEKLY, "2026W15")).isZero();
    }

    @DisplayName("대량 데이터: 1000상품 × 7일 → TOP 100 정확 적재")
    @Test
    void largeDataset_top100() throws Exception {
        LocalDate monday = LocalDate.of(2026, 4, 6);
        seedScoreDaily(1000, 7, monday);

        JobParameters params = new JobParametersBuilder()
                .addString("date", "20260408")
                .toJobParameters();
        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<MvProductRankRow> results = mvProductRankRepository.findByPeriodKey(
                RankPeriodType.WEEKLY, "2026W15", 0, 200);
        assertThat(results).hasSize(100);
        assertThat(results.get(0).rankNo()).isEqualTo(1);
        assertThat(results.get(0).refProductId()).isEqualTo(1000L);
    }

    private void insertScoreDaily(Long productDbId, LocalDate date, double score,
                                   long viewCount, long likeCount, BigDecimal orderAmount) {
        jdbcTemplate.update(
                "INSERT INTO mv_product_score_daily (product_db_id, score_date, score, view_count, like_count, order_amount, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())",
                productDbId, date, score, viewCount, likeCount, orderAmount
        );
    }

    private void seedScoreDaily(int productCount, int days, LocalDate startDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO mv_product_score_daily (product_db_id, score_date, score, view_count, like_count, order_amount, created_at, updated_at) VALUES ");
        boolean first = true;
        for (int day = 0; day < days; day++) {
            LocalDate date = startDate.plusDays(day);
            for (int productId = 1; productId <= productCount; productId++) {
                if (!first) sb.append(",");
                first = false;
                double score = productId * 10.0 + day;
                sb.append(String.format("(%d,'%s',%.1f,%d,%d,%.2f,NOW(),NOW())",
                        productId, date, score, productId * 10L, productId * 5L, productId * 100.0));
            }
        }
        jdbcTemplate.execute(sb.toString());
    }
}
