package com.loopers.batch.job.ranking.weekly;

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
@TestPropertySource(properties = "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME)
@DisplayName("WeeklyRankingJob E2E")
class WeeklyRankingJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(WeeklyRankingJobConfig.JOB_NAME)
    private Job weeklyRankingJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(weeklyRankingJob);
        // batch 모듈은 MV/tmp/daily 테이블의 @Entity를 선언하지 않아 ddl-auto가 생성하지 않는다 → 수동 생성.
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
                CREATE TABLE IF NOT EXISTS mv_product_rank_weekly (
                    base_date     DATE          NOT NULL,
                    product_id    BIGINT        NOT NULL,
                    rank_no       INT           NOT NULL,
                    score         DECIMAL(18,4) NOT NULL,
                    like_count    BIGINT        NOT NULL,
                    order_count   BIGINT        NOT NULL,
                    view_count    BIGINT        NOT NULL,
                    order_amount  BIGINT        NOT NULL,
                    aggregated_at DATETIME      NOT NULL,
                    PRIMARY KEY (base_date, product_id)
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tmp_weekly_aggregate (
                    product_id   BIGINT        NOT NULL PRIMARY KEY,
                    view_count   BIGINT        NOT NULL DEFAULT 0,
                    like_count   BIGINT        NOT NULL DEFAULT 0,
                    order_count  BIGINT        NOT NULL DEFAULT 0,
                    order_amount BIGINT        NOT NULL DEFAULT 0,
                    score        DECIMAL(18,4) NOT NULL
                ) ENGINE=InnoDB
                """);
        jdbcTemplate.update("DELETE FROM product_metrics_daily");
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.update("TRUNCATE TABLE tmp_weekly_aggregate");
    }

    @Test
    @DisplayName("7일 윈도우 집계 → TOP N이 score DESC, productId ASC로 MV에 적재된다")
    void aggregate_topN_isOrderedByScoreDesc() throws Exception {
        // given — baseDate = 오늘, 윈도우 [오늘-6, 오늘]
        LocalDate baseDate = LocalDate.of(2026, 4, 15);
        // 상품 A: 주문 10 / 좋아요 5 / 조회 100  → score = 10*0.7 + 5*0.2 + 100*0.1 = 18.0
        // 상품 B: 주문 3  / 좋아요 20 / 조회 200 → score = 3*0.7 + 20*0.2 + 200*0.1 = 26.1
        // 상품 C: 주문 1  / 좋아요 1 / 조회 1    → score = 0.7 + 0.2 + 0.1 = 1.0
        insertMetric(1L, baseDate.minusDays(3), 50, 3, 5, 5000);
        insertMetric(1L, baseDate.minusDays(1), 50, 2, 5, 5000);
        insertMetric(2L, baseDate.minusDays(2), 100, 10, 2, 2000);
        insertMetric(2L, baseDate, 100, 10, 1, 1000);
        insertMetric(3L, baseDate, 1, 1, 1, 100);
        // 윈도우 밖 — 집계되면 안 됨
        insertMetric(99L, baseDate.minusDays(30), 9999, 9999, 9999, 9999999);

        // when
        var jobExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("baseDate", "20260415")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters());

        // then
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT product_id, rank_no, score
                  FROM mv_product_rank_weekly
                 WHERE base_date = ?
                 ORDER BY rank_no
                """, baseDate);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).get("product_id")).isEqualTo(2L);
        assertThat(rows.get(1).get("product_id")).isEqualTo(1L);
        assertThat(rows.get(2).get("product_id")).isEqualTo(3L);

        // 윈도우 밖 product 99는 미포함
        Long cnt99 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE product_id = 99",
                Long.class);
        assertThat(cnt99).isZero();
    }

    @Test
    @DisplayName("같은 baseDate로 재실행 시 결과가 멱등하게 교체된다")
    void idempotent_rerun() throws Exception {
        LocalDate baseDate = LocalDate.of(2026, 4, 15);
        insertMetric(1L, baseDate, 10, 1, 1, 1000);

        // first run
        var firstRun = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("baseDate", "20260415")
                .addLong("run.id", 1L)
                .toJobParameters());
        assertThat(firstRun.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        Long firstCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE base_date = ?",
                Long.class, baseDate);

        // second run — same base_date, no extra rows
        var secondRun = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("baseDate", "20260415")
                .addLong("run.id", 2L)
                .toJobParameters());
        assertThat(secondRun.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        Long secondCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE base_date = ?",
                Long.class, baseDate);
        assertThat(secondCount).isEqualTo(firstCount);
    }

    private void insertMetric(long productId, LocalDate date, long view, long like, long order, long amount) {
        jdbcTemplate.update("""
                INSERT INTO product_metrics_daily
                    (product_id, metric_date, view_count, like_count, order_count, order_amount, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                """, productId, date, view, like, order, amount);
    }
}
