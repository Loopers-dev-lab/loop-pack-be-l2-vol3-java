package com.loopers.batch.job;

import com.loopers.config.redis.RedisConfig;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "spring.batch.job.name=" + ProductMetricsDailyAggregationJobConfig.MONTHLY_JOB_NAME
})
@SpringBatchTest
@ImportTestcontainers({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@ActiveProfiles("test")
class ProductMetricsMonthlyAggregationJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier(ProductMetricsDailyAggregationJobConfig.MONTHLY_JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
        ensureAggregationTables();
        clearStorage();
    }

    @AfterEach
    void tearDown() {
        clearStorage();
    }

    @Test
    @DisplayName("월간 집계 잡이 MySQL과 Redis에 집계 결과를 적재한다")
    void aggregatesMonthlyMetricsIntoMysqlAndRedis() throws Exception {
        LocalDate requestDate = LocalDate.of(2025, 9, 9);
        seedMetrics(requestDate.minusDays(29), requestDate, "product-001", 1, 1, 10, 2);
        seedMetrics(requestDate.minusDays(29), requestDate, "product-002", 0, 1, 7, 1);
        insertMetric(requestDate.minusDays(30), "product-out-of-range", 100, 100, 1000, 100);

        JobExecution jobExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addLocalDate("requestDate", requestDate)
                .toJobParameters());

        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(loadMonthlyRows(requestDate)).containsExactly(
                new RankingBatchRow(requestDate.minusDays(29), requestDate, "product-001", 30, 30, 300, 60, new BigDecimal("222.0")),
                new RankingBatchRow(requestDate.minusDays(29), requestDate, "product-002", 0, 30, 210, 30, new BigDecimal("150.0"))
        );

        String rankingKey = "ranking:monthly:20250909";
        assertThat(redisTemplate.opsForZSet().reverseRange(rankingKey, 0, -1))
                .containsExactly("product-001", "product-002");
        assertThat(redisTemplate.opsForZSet().score(rankingKey, "product-001")).isEqualTo(222.0d);
        assertThat(redisTemplate.opsForZSet().score(rankingKey, "product-002")).isEqualTo(150.0d);
    }

    private void clearStorage() {
        jobRepositoryTestUtils.removeJobExecutions();
        jdbcTemplate.update("DELETE FROM product_ranking_weekly_batch");
        jdbcTemplate.update("DELETE FROM product_ranking_monthly_batch");
        jdbcTemplate.update("DELETE FROM product_metrics_daily");
        redisCleanUp.truncateAll();
    }

    private void ensureAggregationTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_metrics_daily (
                    metric_date DATE NOT NULL,
                    product_id VARCHAR(36) NOT NULL,
                    like_count BIGINT NOT NULL DEFAULT 0,
                    sales_count BIGINT NOT NULL DEFAULT 0,
                    sales_amount BIGINT NOT NULL DEFAULT 0,
                    view_count BIGINT NOT NULL DEFAULT 0,
                    version BIGINT NOT NULL DEFAULT 0,
                    updated_at DATETIME(6) NOT NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (metric_date, product_id),
                    KEY idx_product_metrics_daily_product_date (product_id, metric_date),
                    KEY idx_product_metrics_daily_date (metric_date)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_ranking_weekly_batch (
                    period_start_date DATE NOT NULL,
                    period_end_date DATE NOT NULL,
                    product_id VARCHAR(36) NOT NULL,
                    like_count BIGINT NOT NULL DEFAULT 0,
                    sales_count BIGINT NOT NULL DEFAULT 0,
                    sales_amount BIGINT NOT NULL DEFAULT 0,
                    view_count BIGINT NOT NULL DEFAULT 0,
                    ranking_score DECIMAL(18,1) NOT NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (period_end_date, product_id),
                    KEY idx_product_ranking_weekly_batch_end_date (period_end_date),
                    KEY idx_product_ranking_weekly_batch_score (period_end_date, ranking_score)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_ranking_monthly_batch (
                    period_start_date DATE NOT NULL,
                    period_end_date DATE NOT NULL,
                    product_id VARCHAR(36) NOT NULL,
                    like_count BIGINT NOT NULL DEFAULT 0,
                    sales_count BIGINT NOT NULL DEFAULT 0,
                    sales_amount BIGINT NOT NULL DEFAULT 0,
                    view_count BIGINT NOT NULL DEFAULT 0,
                    ranking_score DECIMAL(18,1) NOT NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (period_end_date, product_id),
                    KEY idx_product_ranking_monthly_batch_end_date (period_end_date),
                    KEY idx_product_ranking_monthly_batch_score (period_end_date, ranking_score)
                )
                """);
    }

    private void seedMetrics(LocalDate startDate, LocalDate endDate, String productId, long likeCount, long salesCount, long salesAmount, long viewCount) {
        for (LocalDate metricDate = startDate; !metricDate.isAfter(endDate); metricDate = metricDate.plusDays(1)) {
            insertMetric(metricDate, productId, likeCount, salesCount, salesAmount, viewCount);
        }
    }

    private void insertMetric(LocalDate metricDate, String productId, long likeCount, long salesCount, long salesAmount, long viewCount) {
        jdbcTemplate.update(
                """
                INSERT INTO product_metrics_daily (
                    metric_date,
                    product_id,
                    like_count,
                    sales_count,
                    sales_amount,
                    view_count,
                    version,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 0, ?)
                """,
                Date.valueOf(metricDate),
                productId,
                likeCount,
                salesCount,
                salesAmount,
                viewCount,
                Timestamp.valueOf(metricDate.atStartOfDay())
        );
    }

    private List<RankingBatchRow> loadMonthlyRows(LocalDate requestDate) {
        return jdbcTemplate.query(
                """
                SELECT period_start_date,
                       period_end_date,
                       product_id,
                       like_count,
                       sales_count,
                       sales_amount,
                       view_count,
                       ranking_score
                FROM product_ranking_monthly_batch
                WHERE period_end_date = ?
                ORDER BY ranking_score DESC, product_id ASC
                """,
                (rs, rowNum) -> new RankingBatchRow(
                        rs.getDate("period_start_date").toLocalDate(),
                        rs.getDate("period_end_date").toLocalDate(),
                        rs.getString("product_id"),
                        rs.getLong("like_count"),
                        rs.getLong("sales_count"),
                        rs.getLong("sales_amount"),
                        rs.getLong("view_count"),
                        rs.getBigDecimal("ranking_score")
                ),
                Date.valueOf(requestDate)
        );
    }

    private record RankingBatchRow(
            LocalDate periodStartDate,
            LocalDate periodEndDate,
            String productId,
            long likeCount,
            long salesCount,
            long salesAmount,
            long viewCount,
            BigDecimal rankingScore
    ) {
    }
}
