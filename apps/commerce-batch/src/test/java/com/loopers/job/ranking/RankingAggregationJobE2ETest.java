package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.RankingAggregationJobConfig;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + RankingAggregationJobConfig.JOB_NAME)
class RankingAggregationJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(RankingAggregationJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MvProductRankWeeklyJpaRepository weeklyRepository;

    @Autowired
    private MvProductRankMonthlyJpaRepository monthlyRepository;

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 4, 13);
    private static final AtomicLong RUN_ID = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.execute("DELETE FROM mv_product_rank_monthly");
        jdbcTemplate.execute("DELETE FROM product_metrics");
        jdbcTemplate.execute("DELETE FROM products");

        // 기본 상품 데이터 (활성 상태)
        for (long id = 1; id <= 4; id++) {
            insertProduct(id, false);
        }
    }

    @DisplayName("baseDate 파라미터 없이 실행하면 Job이 실패한다")
    @Test
    void failsWithoutBaseDate() throws Exception {
        jobLauncherTestUtils.setJob(job);

        var jobExecution = jobLauncherTestUtils.launchJob();

        assertThat(jobExecution.getExitStatus().getExitCode())
            .isEqualTo(ExitStatus.FAILED.getExitCode());
    }

    @DisplayName("product_metrics 데이터를 읽어 주간/월간 MV 테이블에 TOP 100을 적재한다")
    @Test
    void aggregatesWeeklyAndMonthlyRankings() throws Exception {
        jobLauncherTestUtils.setJob(job);

        // 최근 7일간 데이터 적재 (saturation k=100 기준 점수)
        insertMetrics(1L, BASE_DATE, 100, 50, 10);          // score ≈ 0.1803
        insertMetrics(2L, BASE_DATE, 200, 30, 5);            // score ≈ 0.1462
        insertMetrics(3L, BASE_DATE.minusDays(3), 50, 20, 3);// score ≈ 0.0870
        // 8일 전 데이터 - 주간에는 미포함, 월간에는 포함
        insertMetrics(4L, BASE_DATE.minusDays(8), 300, 100, 20); // score ≈ 0.2917

        var jobParameters = new JobParametersBuilder()
            .addLocalDate("baseDate", BASE_DATE)
            .addLong("run.id", RUN_ID.getAndIncrement())
            .toJobParameters();
        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        assertThat(jobExecution.getExitStatus().getExitCode())
            .isEqualTo(ExitStatus.COMPLETED.getExitCode());

        // 주간: 상품 1, 2, 3만 포함 (4는 8일 전이라 제외)
        // saturation 적용 시 like/sales 비율이 좋은 상품1이 상품2보다 앞선다.
        var weeklyRanks = weeklyRepository.findByAggregatedAtOrderByRankAsc(BASE_DATE);
        assertThat(weeklyRanks).hasSize(3);
        assertThat(weeklyRanks.get(0).getProductId()).isEqualTo(1L); // 0.1803 → 1위
        assertThat(weeklyRanks.get(1).getProductId()).isEqualTo(2L); // 0.1462 → 2위
        assertThat(weeklyRanks.get(2).getProductId()).isEqualTo(3L); // 0.0870 → 3위

        // 월간: 모든 상품 포함
        var monthlyRanks = monthlyRepository.findByAggregatedAtOrderByRankAsc(BASE_DATE);
        assertThat(monthlyRanks).hasSize(4);
        assertThat(monthlyRanks.get(0).getProductId()).isEqualTo(4L); // 0.2917 → 1위
    }

    @DisplayName("같은 상품이 여러 날짜에 걸쳐 있으면 합산된다")
    @Test
    void aggregatesMultipleDaysForSameProduct() throws Exception {
        jobLauncherTestUtils.setJob(job);

        // 상품 1: 3일에 걸쳐 분산
        insertMetrics(1L, BASE_DATE, 50, 10, 5);
        insertMetrics(1L, BASE_DATE.minusDays(1), 30, 20, 3);
        insertMetrics(1L, BASE_DATE.minusDays(2), 20, 10, 2);
        // 합계: view=100, like=40, sales=10
        // saturation 점수 (k=100):
        //   (100/200)*0.1 + (40/140)*0.2 + (10/110)*0.7 ≈ 0.1708

        var jobParameters = new JobParametersBuilder()
            .addLocalDate("baseDate", BASE_DATE)
            .addLong("run.id", RUN_ID.getAndIncrement())
            .toJobParameters();
        jobLauncherTestUtils.launchJob(jobParameters);

        var weeklyRanks = weeklyRepository.findByAggregatedAtOrderByRankAsc(BASE_DATE);
        assertThat(weeklyRanks).hasSize(1);
        assertThat(weeklyRanks.get(0).getScore()).isCloseTo(0.1708, within(0.0001));
        assertThat(weeklyRanks.get(0).getViewCount()).isEqualTo(100);
        assertThat(weeklyRanks.get(0).getLikeCount()).isEqualTo(40);
        assertThat(weeklyRanks.get(0).getSalesCount()).isEqualTo(10);
    }

    @DisplayName("재실행 시 기존 데이터를 교체한다 (멱등성)")
    @Test
    void idempotentReExecution() throws Exception {
        jobLauncherTestUtils.setJob(job);

        insertMetrics(1L, BASE_DATE, 100, 50, 10);

        var jobParameters1 = new JobParametersBuilder()
            .addLocalDate("baseDate", BASE_DATE)
            .addLong("run.id", RUN_ID.getAndIncrement())
            .toJobParameters();
        jobLauncherTestUtils.launchJob(jobParameters1);

        assertThat(weeklyRepository.findByAggregatedAtOrderByRankAsc(BASE_DATE)).hasSize(1);

        // 데이터 변경 후 재실행
        jdbcTemplate.execute("DELETE FROM product_metrics");
        insertMetrics(1L, BASE_DATE, 200, 100, 20);
        insertMetrics(2L, BASE_DATE, 50, 10, 5);

        var jobParameters2 = new JobParametersBuilder()
            .addLocalDate("baseDate", BASE_DATE)
            .addLong("run.id", RUN_ID.getAndIncrement())
            .toJobParameters();
        jobLauncherTestUtils.launchJob(jobParameters2);

        var weeklyRanks = weeklyRepository.findByAggregatedAtOrderByRankAsc(BASE_DATE);
        assertThat(weeklyRanks).hasSize(2);
    }

    @DisplayName("soft delete된 상품은 랭킹 집계에서 제외된다")
    @Test
    void excludesSoftDeletedProducts() throws Exception {
        jobLauncherTestUtils.setJob(job);

        // 상품 3을 삭제 상태로 변경
        jdbcTemplate.update("UPDATE products SET deleted_at = '2026-04-01 00:00:00' WHERE id = 3");

        insertMetrics(1L, BASE_DATE, 100, 50, 10);   // saturation score ≈ 0.1803
        insertMetrics(2L, BASE_DATE, 200, 30, 5);    // saturation score ≈ 0.1462
        insertMetrics(3L, BASE_DATE, 500, 200, 50);  // 최고점이지만 삭제됨

        var jobParameters = new JobParametersBuilder()
            .addLocalDate("baseDate", BASE_DATE)
            .addLong("run.id", RUN_ID.getAndIncrement())
            .toJobParameters();
        jobLauncherTestUtils.launchJob(jobParameters);

        var weeklyRanks = weeklyRepository.findByAggregatedAtOrderByRankAsc(BASE_DATE);
        assertThat(weeklyRanks).hasSize(2);
        assertThat(weeklyRanks.get(0).getProductId()).isEqualTo(1L); // 0.1803 → 1위
        assertThat(weeklyRanks.get(1).getProductId()).isEqualTo(2L); // 0.1462 → 2위
        // 삭제된 상품 3은 제외
    }

    private void insertProduct(Long productId, boolean deleted) {
        jdbcTemplate.update(
            "INSERT INTO products (id, brand_id, name, price, stock, like_count, version, created_at, updated_at, deleted_at) " +
                "VALUES (?, 1, ?, 10000, 100, 0, 0, NOW(), NOW(), ?)",
            productId, "상품" + productId, deleted ? java.sql.Timestamp.valueOf("2026-04-01 00:00:00") : null
        );
    }

    private void insertMetrics(Long productId, LocalDate metricDate,
                               long viewCount, long likeCount, long salesCount) {
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, metric_date, view_count, like_count, sales_count) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE view_count = view_count + ?, like_count = like_count + ?, sales_count = sales_count + ?",
            productId, metricDate, viewCount, likeCount, salesCount,
            viewCount, likeCount, salesCount
        );
    }
}
