package com.loopers.batch.job.monthlyranking;

import com.loopers.infrastructure.persistence.jpa.rank.ProductRankMonthlyJpaEntity;
import com.loopers.infrastructure.persistence.jpa.rank.ProductRankMonthlyJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@DisplayName("MonthlyRankingJob 테스트")
class MonthlyRankingJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("monthlyRankingJob")
    private Job monthlyRankingJob;

    @Autowired
    private ProductRankMonthlyJpaRepository monthlyRankRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(monthlyRankingJob);
        cleanUpTestData();
    }

    private void cleanUpTestData() {
        jdbcTemplate.execute("DELETE FROM mv_product_rank_monthly");
        jdbcTemplate.execute("DELETE FROM product_metrics_daily");
    }

    @Nested
    @DisplayName("월간 랭킹 집계")
    class MonthlyRankingAggregation {

        @Test
        @DisplayName("월간 메트릭 데이터를 집계하여 TOP 100 랭킹을 생성한다")
        void aggregateMonthlyMetrics() throws Exception {
            // Arrange - 2025년 1월 데이터 (1일~31일)
            insertDailyMetrics(100L, LocalDate.of(2025, 1, 5), 10, 5, 2, BigDecimal.valueOf(3.0));
            insertDailyMetrics(100L, LocalDate.of(2025, 1, 15), 20, 10, 3, BigDecimal.valueOf(5.5));
            insertDailyMetrics(100L, LocalDate.of(2025, 1, 25), 15, 8, 2, BigDecimal.valueOf(4.0));
            insertDailyMetrics(200L, LocalDate.of(2025, 1, 10), 50, 20, 10, BigDecimal.valueOf(15.0));
            insertDailyMetrics(300L, LocalDate.of(2025, 1, 20), 5, 2, 1, BigDecimal.valueOf(1.5));

            JobParameters params = new JobParametersBuilder()
                .addString("targetDate", "20250115")
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

            // Act
            JobExecution jobExecution = jobLauncherTestUtils.launchJob(params);

            // Assert
            assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            List<ProductRankMonthlyJpaEntity> rankings = monthlyRankRepository
                .findByPeriodStartDateOrderByRankNumberAsc(LocalDate.of(2025, 1, 1));

            assertThat(rankings).hasSize(3);

            // 1위: product 200 (score: 15.0)
            assertThat(rankings.get(0).getProductId()).isEqualTo(200L);
            assertThat(rankings.get(0).getRankNumber()).isEqualTo(1);
            assertThat(rankings.get(0).getTotalScore()).isEqualByComparingTo(BigDecimal.valueOf(15.0));

            // 2위: product 100 (score: 3.0 + 5.5 + 4.0 = 12.5)
            assertThat(rankings.get(1).getProductId()).isEqualTo(100L);
            assertThat(rankings.get(1).getRankNumber()).isEqualTo(2);
            assertThat(rankings.get(1).getTotalScore()).isEqualByComparingTo(BigDecimal.valueOf(12.5));
            assertThat(rankings.get(1).getTotalViewCount()).isEqualTo(45); // 10 + 20 + 15
            assertThat(rankings.get(1).getTotalLikeCount()).isEqualTo(23); // 5 + 10 + 8
            assertThat(rankings.get(1).getTotalOrderCount()).isEqualTo(7); // 2 + 3 + 2

            // 3위: product 300 (score: 1.5)
            assertThat(rankings.get(2).getProductId()).isEqualTo(300L);
            assertThat(rankings.get(2).getRankNumber()).isEqualTo(3);
        }

        @Test
        @DisplayName("period_start_date와 period_end_date가 정확히 설정된다 (31일 월)")
        void periodDatesForJanuary() throws Exception {
            // Arrange - 2025년 1월 (31일)
            insertDailyMetrics(100L, LocalDate.of(2025, 1, 15), 10, 5, 2, BigDecimal.valueOf(3.0));

            JobParameters params = new JobParametersBuilder()
                .addString("targetDate", "20250115")
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

            // Act
            JobExecution jobExecution = jobLauncherTestUtils.launchJob(params);

            // Assert
            assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            List<ProductRankMonthlyJpaEntity> rankings = monthlyRankRepository
                .findByPeriodStartDateOrderByRankNumberAsc(LocalDate.of(2025, 1, 1));

            assertThat(rankings).hasSize(1);
            assertThat(rankings.get(0).getPeriodStartDate()).isEqualTo(LocalDate.of(2025, 1, 1));
            assertThat(rankings.get(0).getPeriodEndDate()).isEqualTo(LocalDate.of(2025, 1, 31));
        }

        @Test
        @DisplayName("윤년 2월 말일이 정확히 계산된다")
        void leapYearFebruary() throws Exception {
            // Arrange - 2024년 2월 (윤년, 29일)
            insertDailyMetrics(100L, LocalDate.of(2024, 2, 15), 10, 5, 2, BigDecimal.valueOf(3.0));

            JobParameters params = new JobParametersBuilder()
                .addString("targetDate", "20240215")
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

            // Act
            JobExecution jobExecution = jobLauncherTestUtils.launchJob(params);

            // Assert
            assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            List<ProductRankMonthlyJpaEntity> rankings = monthlyRankRepository
                .findByPeriodStartDateOrderByRankNumberAsc(LocalDate.of(2024, 2, 1));

            assertThat(rankings).hasSize(1);
            assertThat(rankings.get(0).getPeriodStartDate()).isEqualTo(LocalDate.of(2024, 2, 1));
            assertThat(rankings.get(0).getPeriodEndDate()).isEqualTo(LocalDate.of(2024, 2, 29)); // 윤년
        }

        @Test
        @DisplayName("평년 2월 말일이 정확히 계산된다")
        void nonLeapYearFebruary() throws Exception {
            // Arrange - 2025년 2월 (평년, 28일)
            insertDailyMetrics(100L, LocalDate.of(2025, 2, 15), 10, 5, 2, BigDecimal.valueOf(3.0));

            JobParameters params = new JobParametersBuilder()
                .addString("targetDate", "20250215")
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

            // Act
            JobExecution jobExecution = jobLauncherTestUtils.launchJob(params);

            // Assert
            assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            List<ProductRankMonthlyJpaEntity> rankings = monthlyRankRepository
                .findByPeriodStartDateOrderByRankNumberAsc(LocalDate.of(2025, 2, 1));

            assertThat(rankings).hasSize(1);
            assertThat(rankings.get(0).getPeriodStartDate()).isEqualTo(LocalDate.of(2025, 2, 1));
            assertThat(rankings.get(0).getPeriodEndDate()).isEqualTo(LocalDate.of(2025, 2, 28)); // 평년
        }

        @Test
        @DisplayName("30일 월도 정확히 처리된다")
        void thirtyDayMonth() throws Exception {
            // Arrange - 2025년 4월 (30일)
            insertDailyMetrics(100L, LocalDate.of(2025, 4, 15), 10, 5, 2, BigDecimal.valueOf(3.0));

            JobParameters params = new JobParametersBuilder()
                .addString("targetDate", "20250415")
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

            // Act
            JobExecution jobExecution = jobLauncherTestUtils.launchJob(params);

            // Assert
            assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            List<ProductRankMonthlyJpaEntity> rankings = monthlyRankRepository
                .findByPeriodStartDateOrderByRankNumberAsc(LocalDate.of(2025, 4, 1));

            assertThat(rankings).hasSize(1);
            assertThat(rankings.get(0).getPeriodStartDate()).isEqualTo(LocalDate.of(2025, 4, 1));
            assertThat(rankings.get(0).getPeriodEndDate()).isEqualTo(LocalDate.of(2025, 4, 30));
        }
    }

    @Nested
    @DisplayName("멱등성 보장")
    class Idempotency {

        @Test
        @DisplayName("동일 월간에 재실행하면 기존 데이터가 갱신된다")
        void rerunUpdatesExistingData() throws Exception {
            // Arrange - 첫 번째 실행
            insertDailyMetrics(100L, LocalDate.of(2025, 1, 10), 10, 5, 2, BigDecimal.valueOf(3.0));

            JobParameters params1 = new JobParametersBuilder()
                .addString("targetDate", "20250110")
                .addLong("runId", 1L)
                .toJobParameters();

            jobLauncherTestUtils.launchJob(params1);

            // 데이터 추가
            insertDailyMetrics(200L, LocalDate.of(2025, 1, 20), 50, 20, 10, BigDecimal.valueOf(15.0));

            // Act - 두 번째 실행
            JobParameters params2 = new JobParametersBuilder()
                .addString("targetDate", "20250120")
                .addLong("runId", 2L)
                .toJobParameters();

            JobExecution jobExecution = jobLauncherTestUtils.launchJob(params2);

            // Assert
            assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            List<ProductRankMonthlyJpaEntity> rankings = monthlyRankRepository
                .findByPeriodStartDateOrderByRankNumberAsc(LocalDate.of(2025, 1, 1));

            // 중복 없이 2건만 존재
            assertThat(rankings).hasSize(2);

            // 순위가 재계산됨 (200이 1위)
            assertThat(rankings.get(0).getProductId()).isEqualTo(200L);
            assertThat(rankings.get(0).getRankNumber()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("경계 조건")
    class BoundaryConditions {

        @Test
        @DisplayName("월의 첫날 데이터도 집계에 포함된다")
        void firstDayOfMonthIncluded() throws Exception {
            // Arrange
            insertDailyMetrics(100L, LocalDate.of(2025, 1, 1), 10, 5, 2, BigDecimal.valueOf(3.0));

            JobParameters params = new JobParametersBuilder()
                .addString("targetDate", "20250115")
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

            // Act
            JobExecution jobExecution = jobLauncherTestUtils.launchJob(params);

            // Assert
            assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            List<ProductRankMonthlyJpaEntity> rankings = monthlyRankRepository
                .findByPeriodStartDateOrderByRankNumberAsc(LocalDate.of(2025, 1, 1));

            assertThat(rankings).hasSize(1);
            assertThat(rankings.get(0).getTotalViewCount()).isEqualTo(10);
        }

        @Test
        @DisplayName("월의 마지막날 데이터도 집계에 포함된다")
        void lastDayOfMonthIncluded() throws Exception {
            // Arrange
            insertDailyMetrics(100L, LocalDate.of(2025, 1, 31), 10, 5, 2, BigDecimal.valueOf(3.0));

            JobParameters params = new JobParametersBuilder()
                .addString("targetDate", "20250115")
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

            // Act
            JobExecution jobExecution = jobLauncherTestUtils.launchJob(params);

            // Assert
            assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            List<ProductRankMonthlyJpaEntity> rankings = monthlyRankRepository
                .findByPeriodStartDateOrderByRankNumberAsc(LocalDate.of(2025, 1, 1));

            assertThat(rankings).hasSize(1);
            assertThat(rankings.get(0).getTotalViewCount()).isEqualTo(10);
        }

        @Test
        @DisplayName("다른 월의 데이터는 집계에서 제외된다")
        void otherMonthDataExcluded() throws Exception {
            // Arrange - 1월 데이터와 2월 데이터
            insertDailyMetrics(100L, LocalDate.of(2025, 1, 15), 10, 5, 2, BigDecimal.valueOf(3.0));
            insertDailyMetrics(200L, LocalDate.of(2025, 2, 15), 50, 20, 10, BigDecimal.valueOf(15.0)); // 제외 대상

            JobParameters params = new JobParametersBuilder()
                .addString("targetDate", "20250115") // 1월 집계
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

            // Act
            JobExecution jobExecution = jobLauncherTestUtils.launchJob(params);

            // Assert
            assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            List<ProductRankMonthlyJpaEntity> rankings = monthlyRankRepository
                .findByPeriodStartDateOrderByRankNumberAsc(LocalDate.of(2025, 1, 1));

            // 1월 데이터만 집계됨
            assertThat(rankings).hasSize(1);
            assertThat(rankings.get(0).getProductId()).isEqualTo(100L);
        }
    }

    private void insertDailyMetrics(Long productId, LocalDate metricDate, int viewCount, int likeCount, int orderCount, BigDecimal score) {
        jdbcTemplate.update(
            """
            INSERT INTO product_metrics_daily (product_id, metric_date, view_count, like_count, order_count, score, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
            """,
            productId, metricDate, viewCount, likeCount, orderCount, score
        );
    }
}