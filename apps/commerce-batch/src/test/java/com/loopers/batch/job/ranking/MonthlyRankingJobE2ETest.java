package com.loopers.batch.job.ranking;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@Import(MySqlTestContainersConfig.class)
@TestPropertySource(properties = {
    "spring.batch.job.name=" + MonthlyRankingJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
class MonthlyRankingJobE2ETest {

    private static final String YEAR_MONTH = "2026-03";
    // 2026-03 = 2026-03-01 ~ 2026-03-31

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(MonthlyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly");
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly_staging");
        jdbcTemplate.update("DELETE FROM ranking_score_ledger");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly");
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly_staging");
        jdbcTemplate.update("DELETE FROM ranking_score_ledger");
    }

    private JobParameters params(String yearMonth) {
        return new JobParametersBuilder()
            .addString("year_month", yearMonth)
            .addLong("run.id", System.currentTimeMillis())
            .toJobParameters();
    }

    private void insertLedger(long productId, String bucketKey, double basePoints) {
        jdbcTemplate.update(
            "INSERT INTO ranking_score_ledger "
                + "(bucket_type, bucket_key, product_id, base_points, last_scored_at, dirty, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            "DAY", bucketKey, productId, basePoints,
            Timestamp.from(Instant.now()), false,
            Timestamp.from(Instant.now()), Timestamp.from(Instant.now())
        );
    }

    @Nested
    @DisplayName("월간 랭킹 Job 을 정상 실행할 때, ")
    class HappyPath {

        @Test
        void sumsDailyBasePointsOverMonth_andInsertsIntoMv() throws Exception {
            // given: 2026-03 전월 중 일부 날짜에 ledger 데이터 주입
            insertLedger(1L, "20260301", 2.0);
            insertLedger(1L, "20260315", 3.0);
            insertLedger(1L, "20260331", 5.0);  // 월말
            insertLedger(2L, "20260310", 4.0);
            insertLedger(2L, "20260320", 1.0);

            // when
            JobExecution exec = jobLauncherTestUtils.launchJob(params(YEAR_MONTH));

            // then
            assertThat(exec.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT product_id, ranking_position, score FROM mv_product_rank_monthly "
                    + "WHERE year_month_key = ? ORDER BY ranking_position", YEAR_MONTH);

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0)).containsEntry("product_id", 1L);
            assertThat(rows.get(0)).containsEntry("ranking_position", 1L);
            assertThat(((Number) rows.get(0).get("score")).doubleValue()).isEqualTo(10.0);

            assertThat(rows.get(1)).containsEntry("product_id", 2L);
            assertThat(rows.get(1)).containsEntry("ranking_position", 2L);
            assertThat(((Number) rows.get(1).get("score")).doubleValue()).isEqualTo(5.0);
        }

        @Test
        void limitsMvRowsToTop100_whenManyProducts() throws Exception {
            // given: 150 개 상품, 각자 고유 score
            for (long pid = 1; pid <= 150; pid++) {
                insertLedger(pid, "20260315", (double) pid);
            }

            // when
            JobExecution exec = jobLauncherTestUtils.launchJob(params(YEAR_MONTH));

            // then
            assertThat(exec.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

            Long mvCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE year_month_key = ?",
                Long.class, YEAR_MONTH);
            assertThat(mvCount).isEqualTo(100L);

            Map<String, Object> first = jdbcTemplate.queryForMap(
                "SELECT product_id, ranking_position FROM mv_product_rank_monthly "
                    + "WHERE year_month_key = ? AND ranking_position = 1", YEAR_MONTH);
            assertThat(first).containsEntry("product_id", 150L);

            Long minPos = jdbcTemplate.queryForObject(
                "SELECT MIN(ranking_position) FROM mv_product_rank_monthly WHERE year_month_key = ?",
                Long.class, YEAR_MONTH);
            Long maxPos = jdbcTemplate.queryForObject(
                "SELECT MAX(ranking_position) FROM mv_product_rank_monthly WHERE year_month_key = ?",
                Long.class, YEAR_MONTH);
            assertThat(minPos).isEqualTo(1L);
            assertThat(maxPos).isEqualTo(100L);
        }

        @Test
        void filtersOutDataFromOtherMonths() throws Exception {
            // given
            insertLedger(1L, "20260301", 10.0);   // 2026-03 포함
            insertLedger(1L, "20260228", 999.0);  // 2026-02 (이전 달)
            insertLedger(1L, "20260401", 999.0);  // 2026-04 (다음 달)

            // when
            JobExecution exec = jobLauncherTestUtils.launchJob(params(YEAR_MONTH));

            // then
            assertThat(exec.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

            Double score = jdbcTemplate.queryForObject(
                "SELECT score FROM mv_product_rank_monthly WHERE year_month_key = ? AND product_id = ?",
                Double.class, YEAR_MONTH, 1L);
            assertThat(score).isEqualTo(10.0);
        }
    }

    @Nested
    @DisplayName("같은 월로 Job 을 두 번 실행해도, ")
    class Idempotency {

        @Test
        void doesNotDoubleCountScores() throws Exception {
            insertLedger(1L, "20260301", 7.0);

            // first run
            jobLauncherTestUtils.launchJob(params(YEAR_MONTH));
            // second run
            jobLauncherTestUtils.launchJob(params(YEAR_MONTH));

            Double score = jdbcTemplate.queryForObject(
                "SELECT score FROM mv_product_rank_monthly WHERE year_month_key = ? AND product_id = ?",
                Double.class, YEAR_MONTH, 1L);
            assertThat(score).isEqualTo(7.0);
        }
    }
}
