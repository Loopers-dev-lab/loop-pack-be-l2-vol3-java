package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;
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
@TestPropertySource(properties = "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME)
class WeeklyRankingJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(WeeklyRankingJobConfig.JOB_NAME)
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
        jdbcTemplate.execute("DELETE FROM mv_product_rank_weekly");
    }

    @DisplayName("주간 랭킹 배치가 product_daily_metrics를 집계하여 mv_product_rank_weekly에 저장한다")
    @Test
    void weeklyRankingJob_aggregatesMetricsAndSavesToMv() throws Exception {
        // arrange — 2026-04-06(월) ~ 2026-04-12(일) 주간 데이터
        LocalDate targetDate = LocalDate.of(2026, 4, 8);
        LocalDate monday = LocalDate.of(2026, 4, 6);

        insertMetrics(1L, monday, 40, 20, 4000);
        insertMetrics(1L, monday.plusDays(1), 30, 15, 3000);
        insertMetrics(1L, monday.plusDays(2), 30, 15, 3000);
        insertMetrics(2L, monday, 200, 30, 5000);
        insertMetrics(3L, monday.plusDays(1), 10, 5, 50000);

        // act
        var jobParameters = new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .addLong("run.id", 1L)
                .toJobParameters();
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT * FROM mv_product_rank_weekly WHERE ranking_week = '2026-W15' ORDER BY ranking"
        );

        assertAll(
                () -> assertThat(results).hasSize(3),
                () -> assertThat(results.get(0).get("ranking")).isEqualTo(1),
                () -> assertThat(results.get(1).get("ranking")).isEqualTo(2),
                () -> assertThat(results.get(2).get("ranking")).isEqualTo(3),
                () -> assertThat(results.stream().map(r -> r.get("product_id")).toList())
                        .containsExactlyInAnyOrder(1L, 2L, 3L)
        );

        Map<String, Object> top1 = results.get(0);
        assertThat(((Number) top1.get("score")).doubleValue()).isGreaterThan(0);
    }

    @DisplayName("주간 범위 밖의 데이터는 집계에 포함되지 않는다")
    @Test
    void weeklyRankingJob_excludesOutOfRangeData() throws Exception {
        // arrange — 2026-03-30(월) ~ 2026-04-05(일) 주간
        LocalDate targetDate = LocalDate.of(2026, 4, 1);

        insertMetrics(1L, LocalDate.of(2026, 3, 30), 100, 50, 10000);
        insertMetrics(2L, LocalDate.of(2026, 3, 29), 999, 999, 999999); // 이전 주
        insertMetrics(3L, LocalDate.of(2026, 4, 6), 999, 999, 999999);  // 다음 주

        // act
        var jobParameters = new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .addLong("run.id", 10L)
                .toJobParameters();
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // assert
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT * FROM mv_product_rank_weekly WHERE ranking_week = '2026-W14'"
        );

        assertAll(
                () -> assertThat(results).hasSize(1),
                () -> assertThat(results.get(0).get("product_id")).isEqualTo(1L)
        );
    }

    @DisplayName("동일 주간에 배치를 두 번 실행해도 멱등성이 보장된다 (UPSERT)")
    @Test
    void weeklyRankingJob_idempotent() throws Exception {
        // arrange — 2026-01-05(월) ~ 2026-01-11(일)
        LocalDate targetDate = LocalDate.of(2026, 1, 7);
        insertMetrics(1L, LocalDate.of(2026, 1, 5), 100, 50, 10000);

        // act — 1차 실행
        var jobParameters1 = new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .addLong("run.id", 20L)
                .toJobParameters();
        jobLauncherTestUtils.launchJob(jobParameters1);

        // 2차 실행 (run.id 변경으로 재실행 가능)
        var jobParameters2 = new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .addLong("run.id", 21L)
                .toJobParameters();
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters2);

        // assert — 중복 없이 1건만 존재
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT * FROM mv_product_rank_weekly WHERE ranking_week = '2026-W02'"
        );
        assertThat(results).hasSize(1);
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
