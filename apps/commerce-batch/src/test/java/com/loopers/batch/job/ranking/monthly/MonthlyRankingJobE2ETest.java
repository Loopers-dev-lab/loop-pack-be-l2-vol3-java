package com.loopers.batch.job.ranking.monthly;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@Import(MySqlTestContainersConfig.class)
@TestPropertySource(properties = "spring.batch.job.name=" + MonthlyRankingJobConfig.JOB_NAME)
@DisplayName("MonthlyRankingJob E2E")
class MonthlyRankingJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(MonthlyRankingJobConfig.JOB_NAME)
    private Job monthlyRankingJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(monthlyRankingJob);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_metrics_daily (
                    product_id   BIGINT   NOT NULL,
                    metric_date  DATE     NOT NULL,
                    view_count   BIGINT   NOT NULL DEFAULT 0,
                    like_count   BIGINT   NOT NULL DEFAULT 0,
                    order_count  BIGINT   NOT NULL DEFAULT 0,
                    order_amount BIGINT   NOT NULL DEFAULT 0,
                    updated_at   DATETIME NOT NULL,
                    PRIMARY KEY (metric_date, product_id)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mv_product_rank_monthly (
                    `year_month`  VARCHAR(7)    NOT NULL,
                    product_id    BIGINT        NOT NULL,
                    rank_no       INT           NOT NULL,
                    score         DECIMAL(18,4) NOT NULL,
                    like_count    BIGINT        NOT NULL,
                    order_count   BIGINT        NOT NULL,
                    view_count    BIGINT        NOT NULL,
                    order_amount  BIGINT        NOT NULL,
                    aggregated_at DATETIME      NOT NULL,
                    PRIMARY KEY (`year_month`, product_id)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tmp_monthly_aggregate (
                    product_id   BIGINT        NOT NULL PRIMARY KEY,
                    view_count   BIGINT        NOT NULL DEFAULT 0,
                    like_count   BIGINT        NOT NULL DEFAULT 0,
                    order_count  BIGINT        NOT NULL DEFAULT 0,
                    order_amount BIGINT        NOT NULL DEFAULT 0,
                    score        DECIMAL(18,4) NOT NULL
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.update("DELETE FROM product_metrics_daily");
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly");
        jdbcTemplate.update("TRUNCATE TABLE tmp_monthly_aggregate");
    }

    @Test
    @DisplayName("baseDate 월 1일부터 baseDate까지 집계 → year_month='yyyy-MM'로 MV 적재")
    void monthlyAggregate_withYearMonthKey() throws Exception {
        // baseDate = 2026-04-15 → 윈도우 [2026-04-01, 2026-04-15]
        LocalDate baseDate = LocalDate.of(2026, 4, 15);
        insertMetric(1L, LocalDate.of(2026, 4, 1), 10, 5, 2, 20000);
        insertMetric(1L, LocalDate.of(2026, 4, 10), 20, 3, 1, 10000);
        insertMetric(2L, LocalDate.of(2026, 4, 5), 100, 10, 5, 50000);
        // 전달 데이터 — 집계되면 안 됨
        insertMetric(99L, LocalDate.of(2026, 3, 31), 9999, 9999, 9999, 9999999);

        var jobExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("baseDate", "20260415")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters());

        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT `year_month`, product_id, rank_no
                  FROM mv_product_rank_monthly
                 WHERE `year_month` = ?
                 ORDER BY rank_no
                """, "2026-04");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("product_id")).isEqualTo(2L);   // score 높음
        assertThat(rows.get(1).get("product_id")).isEqualTo(1L);
        assertThat(rows.get(0).get("year_month")).isEqualTo("2026-04");

        // 전달 product 99는 미포함
        Long cnt99 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE product_id = 99",
                Long.class);
        assertThat(cnt99).isZero();
    }

    private void insertMetric(long productId, LocalDate date, long view, long like, long order, long amount) {
        jdbcTemplate.update("""
                INSERT INTO product_metrics_daily
                    (product_id, metric_date, view_count, like_count, order_count, order_amount, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                """, productId, date, view, like, order, amount);
    }
}
