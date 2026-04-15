package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.MonthlyRankingJobConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + MonthlyRankingJobConfig.JOB_NAME)
class MonthlyRankingJobE2ETest {

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

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_daily_metrics (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    product_id BIGINT NOT NULL,
                    metric_date DATE NOT NULL,
                    view_count BIGINT NOT NULL DEFAULT 0,
                    like_count BIGINT NOT NULL DEFAULT 0,
                    order_amount BIGINT NOT NULL DEFAULT 0,
                    updated_at DATETIME(6) NOT NULL,
                    UNIQUE KEY uk_product_date (product_id, metric_date)
                )
                """);

        jdbcTemplate.execute("TRUNCATE TABLE product_daily_metrics");
        jdbcTemplate.execute("DELETE FROM mv_product_rank_monthly");
    }

    @DisplayName("월간 랭킹 배치가 product_daily_metrics를 집계하여 mv_product_rank_monthly에 저장한다")
    @Test
    void monthlyRankingJob_aggregatesMetricsAndSavesToMv() throws Exception {
        // arrange — 2026년 4월
        LocalDate targetDate = LocalDate.of(2026, 4, 15);

        insertMetrics(1L, LocalDate.of(2026, 4, 1), 50, 20, 5000);
        insertMetrics(1L, LocalDate.of(2026, 4, 10), 50, 20, 5000);
        insertMetrics(1L, LocalDate.of(2026, 4, 15), 50, 20, 5000);
        insertMetrics(2L, LocalDate.of(2026, 4, 5), 300, 100, 50000);
        insertMetrics(3L, LocalDate.of(2026, 4, 20), 10, 5, 1000);

        // act
        var jobParameters = new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .addLong("run.id", 100L)
                .toJobParameters();
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT * FROM mv_product_rank_monthly WHERE ranking_month = '2026-04' ORDER BY ranking"
        );

        assertAll(
                () -> assertThat(results).hasSize(3),
                () -> assertThat(results.get(0).get("ranking")).isEqualTo(1),
                () -> assertThat(results.get(1).get("ranking")).isEqualTo(2),
                () -> assertThat(results.get(2).get("ranking")).isEqualTo(3)
        );

        double score1 = ((Number) results.get(0).get("score")).doubleValue();
        double score2 = ((Number) results.get(1).get("score")).doubleValue();
        assertThat(score1).isGreaterThanOrEqualTo(score2);
    }

    @DisplayName("월간 범위 밖의 데이터는 집계에 포함되지 않는다")
    @Test
    void monthlyRankingJob_excludesOutOfRangeData() throws Exception {
        // arrange — 2026년 3월
        LocalDate targetDate = LocalDate.of(2026, 3, 15);

        insertMetrics(1L, LocalDate.of(2026, 3, 1), 100, 50, 10000);
        insertMetrics(2L, LocalDate.of(2026, 2, 28), 999, 999, 999999); // 2월 (범위 밖)
        insertMetrics(3L, LocalDate.of(2026, 4, 1), 999, 999, 999999);  // 4월 (범위 밖)

        // act
        var jobParameters = new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .addLong("run.id", 110L)
                .toJobParameters();
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT * FROM mv_product_rank_monthly WHERE ranking_month = '2026-03'"
        );

        assertAll(
                () -> assertThat(results).hasSize(1),
                () -> assertThat(results.get(0).get("product_id")).isEqualTo(1L)
        );
    }

    @DisplayName("동일 월에 배치를 두 번 실행해도 멱등성이 보장된다 (UPSERT)")
    @Test
    void monthlyRankingJob_idempotent() throws Exception {
        // arrange — 2026년 1월
        LocalDate targetDate = LocalDate.of(2026, 1, 15);
        insertMetrics(1L, LocalDate.of(2026, 1, 1), 100, 50, 10000);

        // act — 1차 실행
        var jobParameters1 = new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .addLong("run.id", 120L)
                .toJobParameters();
        jobLauncherTestUtils.launchJob(jobParameters1);

        // 2차 실행
        var jobParameters2 = new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .addLong("run.id", 121L)
                .toJobParameters();
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters2);

        // assert — 중복 없이 1건만 존재
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT * FROM mv_product_rank_monthly WHERE ranking_month = '2026-01'"
        );
        assertThat(results).hasSize(1);
    }

    @DisplayName("월간 집계에서 여러 날의 metrics가 정확히 합산된다")
    @Test
    void monthlyRankingJob_correctAggregation() throws Exception {
        // arrange — 2026년 2월
        LocalDate targetDate = LocalDate.of(2026, 2, 15);
        insertMetrics(1L, LocalDate.of(2026, 2, 1), 10, 5, 1000);
        insertMetrics(1L, LocalDate.of(2026, 2, 10), 20, 10, 2000);
        insertMetrics(1L, LocalDate.of(2026, 2, 20), 30, 15, 3000);

        // act
        var jobParameters = new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .addLong("run.id", 130L)
                .toJobParameters();
        jobLauncherTestUtils.launchJob(jobParameters);

        // assert — 합산 검증 (10+20+30=60, 5+10+15=30, 1000+2000+3000=6000)
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT * FROM mv_product_rank_monthly WHERE ranking_month = '2026-02'"
        );

        assertAll(
                () -> assertThat(results).hasSize(1),
                () -> assertThat(((Number) results.get(0).get("view_count")).longValue()).isEqualTo(60L),
                () -> assertThat(((Number) results.get(0).get("like_count")).longValue()).isEqualTo(30L),
                () -> assertThat(((Number) results.get(0).get("order_amount")).longValue()).isEqualTo(6000L),
                () -> assertThat(((Number) results.get(0).get("ranking")).intValue()).isEqualTo(1)
        );
    }

    private void insertMetrics(Long productId, LocalDate date, long views, long likes, long orderAmount) {
        jdbcTemplate.update("""
                INSERT INTO product_daily_metrics (product_id, metric_date, view_count, like_count, order_amount, updated_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                ON DUPLICATE KEY UPDATE
                    view_count = view_count + VALUES(view_count),
                    like_count = like_count + VALUES(like_count),
                    order_amount = order_amount + VALUES(order_amount),
                    updated_at = NOW()
                """, productId, date, views, likes, orderAmount);
    }
}
