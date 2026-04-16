package com.loopers.job.rankingmv;

import com.loopers.batch.job.rankingmv.ProductRankingMvJobConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
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
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + ProductRankingMvJobConfig.JOB_NAME)
@Sql(scripts = "/schema-batch-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ProductRankingMvJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(ProductRankingMvJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String TARGET_DATE = "20260416";

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly");
        jdbcTemplate.update("DELETE FROM mv_product_rank_staging");
        jdbcTemplate.update("DELETE FROM product_metrics");
        jdbcTemplate.update("DELETE FROM product");
    }

    private void seedProducts(int count) {
        for (int i = 1; i <= count; i++) {
            jdbcTemplate.update(
                "INSERT INTO product (id, brand_id, name, price, stock_quantity, like_count, created_at, updated_at) " +
                "VALUES (?, 1, ?, ?, 1000, 0, NOW(), NOW())",
                i, "상품" + i, i * 1000);
        }
    }

    private void seedMetrics(int productCount, int days, String endDateStr) {
        LocalDate endDate = LocalDate.parse(endDateStr, DATE_FORMATTER);
        for (int d = 0; d < days; d++) {
            LocalDate date = endDate.minusDays(d);
            for (int p = 1; p <= productCount; p++) {
                jdbcTemplate.update(
                    "INSERT INTO product_metrics " +
                    "(product_id, metric_date, view_count, like_count, unlike_count, " +
                    "sales_count, sales_amount, cancel_count_by_event_date, cancel_amount_by_event_date, " +
                    "cancel_count_by_order_date, cancel_amount_by_order_date) " +
                    "VALUES (?, ?, ?, ?, 0, ?, ?, 0, 0, 0, 0)",
                    p, date, p * 100, p * 10, p * 5, p * 50000L);
            }
        }
    }

    private JobExecution runJob(String scope) throws Exception {
        var params = new JobParametersBuilder()
            .addString("targetDate", TARGET_DATE)
            .addString("scope", scope)
            .addLong("run.id", System.currentTimeMillis())
            .toJobParameters();
        return jobLauncherTestUtils.launchJob(params);
    }

    @Nested
    @DisplayName("주간 랭킹 Job")
    class WeeklyJob {

        @Test
        @DisplayName("정상 실행 — 시드 데이터 기반 주간 TOP 100 적재")
        void success() throws Exception {
            seedProducts(150);
            seedMetrics(150, 7, TARGET_DATE);

            JobExecution execution = runJob("weekly");

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            int mvCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key = ?",
                Integer.class, TARGET_DATE);
            assertThat(mvCount).isEqualTo(100);

            // 1위 검증: product_id가 높을수록 메트릭이 높으므로 150이 1위
            Long topProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_weekly WHERE period_key = ? AND ranking = 1",
                Long.class, TARGET_DATE);
            assertThat(topProductId).isEqualTo(150L);

            // 스테이징은 전체 상품 (150건)
            int stagingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_staging WHERE period_key = ?",
                Integer.class, TARGET_DATE);
            assertThat(stagingCount).isEqualTo(150);
        }

        @Test
        @DisplayName("상품이 100개 미만이면 있는 만큼만 적재")
        void lessThan100Products() throws Exception {
            seedProducts(30);
            seedMetrics(30, 7, TARGET_DATE);

            JobExecution execution = runJob("weekly");

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            int mvCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key = ?",
                Integer.class, TARGET_DATE);
            assertThat(mvCount).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("월간 랭킹 Job")
    class MonthlyJob {

        @Test
        @DisplayName("정상 실행 — 30일 데이터 집계")
        void success() throws Exception {
            seedProducts(50);
            seedMetrics(50, 30, TARGET_DATE);

            JobExecution execution = runJob("monthly");

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            int mvCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE period_key = ?",
                Integer.class, TARGET_DATE);
            assertThat(mvCount).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("멱등성")
    class Idempotency {

        @Test
        @DisplayName("같은 파라미터로 2회 실행해도 결과 동일")
        void doubleExecution() throws Exception {
            seedProducts(50);
            seedMetrics(50, 7, TARGET_DATE);

            runJob("weekly");
            JobExecution second = runJob("weekly");

            assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            int mvCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key = ?",
                Integer.class, TARGET_DATE);
            assertThat(mvCount).isEqualTo(50); // 2배가 아님
        }
    }

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("데이터 없는 날짜로 실행 — 빈 MV")
        void noData() throws Exception {
            seedProducts(10); // 메트릭 없음

            JobExecution execution = runJob("weekly");

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            int mvCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key = ?",
                Integer.class, TARGET_DATE);
            assertThat(mvCount).isEqualTo(0);
        }

        @Test
        @DisplayName("7일 미만 데이터 — 있는 만큼만 집계")
        void partialData() throws Exception {
            seedProducts(20);
            seedMetrics(20, 3, TARGET_DATE); // 3일치만

            JobExecution execution = runJob("weekly");

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            int mvCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key = ?",
                Integer.class, TARGET_DATE);
            assertThat(mvCount).isEqualTo(20);
        }

        @Test
        @DisplayName("취소 반영 — cancel_amount가 score에 반영")
        void cancellationReflected() throws Exception {
            seedProducts(2);

            // 상품 1: 매출 100만, 취소 없음
            jdbcTemplate.update(
                "INSERT INTO product_metrics (product_id, metric_date, view_count, like_count, unlike_count, " +
                "sales_count, sales_amount, cancel_count_by_event_date, cancel_amount_by_event_date, " +
                "cancel_count_by_order_date, cancel_amount_by_order_date) VALUES (1, '2026-04-16', 100, 10, 0, 10, 1000000, 0, 0, 0, 0)");

            // 상품 2: 매출 200만, 취소 150만 → 순 매출 50만
            jdbcTemplate.update(
                "INSERT INTO product_metrics (product_id, metric_date, view_count, like_count, unlike_count, " +
                "sales_count, sales_amount, cancel_count_by_event_date, cancel_amount_by_event_date, " +
                "cancel_count_by_order_date, cancel_amount_by_order_date) VALUES (2, '2026-04-16', 200, 20, 0, 20, 2000000, 5, 1500000, 5, 1500000)");

            JobExecution execution = runJob("weekly");

            assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            // 상품 1이 1위 (순 매출 100만 > 상품 2 순 매출 50만)
            Long topProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_weekly WHERE period_key = ? AND ranking = 1",
                Long.class, TARGET_DATE);
            assertThat(topProductId).isEqualTo(1L);
        }
    }
}
