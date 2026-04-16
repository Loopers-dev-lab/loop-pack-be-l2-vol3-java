package com.loopers.batch.job.weeklyranking;

import com.loopers.infrastructure.persistence.jpa.rank.ProductMetricsDailyJpaEntity;
import com.loopers.infrastructure.persistence.jpa.rank.ProductRankWeeklyJpaEntity;
import com.loopers.infrastructure.persistence.jpa.rank.ProductRankWeeklyJpaRepository;
import jakarta.persistence.EntityManager;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@DisplayName("WeeklyRankingJob 테스트")
class WeeklyRankingJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job weeklyRankingJob;

    @Autowired
    private ProductRankWeeklyJpaRepository weeklyRankRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(weeklyRankingJob);
        cleanUpTestData();
    }

    private void cleanUpTestData() {
        jdbcTemplate.execute("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.execute("DELETE FROM product_metrics_daily");
    }

    @Nested
    @DisplayName("주간 랭킹 집계")
    class WeeklyRankingAggregation {

        @Test
        @DisplayName("주간 메트릭 데이터를 집계하여 TOP 100 랭킹을 생성한다")
        void aggregateWeeklyMetrics() throws Exception {
            // Arrange - 2025년 1월 6일(월) ~ 1월 12일(일) 주간 데이터
            insertDailyMetrics(100L, LocalDate.of(2025, 1, 6), 10, 5, 2, BigDecimal.valueOf(3.0));
            insertDailyMetrics(100L, LocalDate.of(2025, 1, 7), 20, 10, 3, BigDecimal.valueOf(5.5));
            insertDailyMetrics(200L, LocalDate.of(2025, 1, 6), 50, 20, 10, BigDecimal.valueOf(15.0));
            insertDailyMetrics(300L, LocalDate.of(2025, 1, 8), 5, 2, 1, BigDecimal.valueOf(1.5));

            JobParameters params = new JobParametersBuilder()
                .addString("targetDate", "20250108")
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

            // Act
            JobExecution jobExecution = jobLauncherTestUtils.launchJob(params);

            // Assert
            assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            List<ProductRankWeeklyJpaEntity> rankings = weeklyRankRepository
                .findByPeriodStartDateOrderByRankNumberAsc(LocalDate.of(2025, 1, 6));

            assertThat(rankings).hasSize(3);

            // 1위: product 200 (score: 15.0)
            assertThat(rankings.get(0).getProductId()).isEqualTo(200L);
            assertThat(rankings.get(0).getRankNumber()).isEqualTo(1);
            assertThat(rankings.get(0).getTotalScore()).isEqualByComparingTo(BigDecimal.valueOf(15.0));

            // 2위: product 100 (score: 3.0 + 5.5 = 8.5)
            assertThat(rankings.get(1).getProductId()).isEqualTo(100L);
            assertThat(rankings.get(1).getRankNumber()).isEqualTo(2);
            assertThat(rankings.get(1).getTotalScore()).isEqualByComparingTo(BigDecimal.valueOf(8.5));
            assertThat(rankings.get(1).getTotalViewCount()).isEqualTo(30); // 10 + 20
            assertThat(rankings.get(1).getTotalLikeCount()).isEqualTo(15); // 5 + 10
            assertThat(rankings.get(1).getTotalOrderCount()).isEqualTo(5); // 2 + 3

            // 3위: product 300 (score: 1.5)
            assertThat(rankings.get(2).getProductId()).isEqualTo(300L);
            assertThat(rankings.get(2).getRankNumber()).isEqualTo(3);
        }

        @Test
        @DisplayName("period_start_date와 period_end_date가 정확히 설정된다")
        void periodDatesAreCorrect() throws Exception {
            // Arrange - 2025년 1월 15일(수) 기준 → 1월 13일(월) ~ 1월 19일(일)
            insertDailyMetrics(100L, LocalDate.of(2025, 1, 15), 10, 5, 2, BigDecimal.valueOf(3.0));

            JobParameters params = new JobParametersBuilder()
                .addString("targetDate", "20250115")
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

            // Act
            JobExecution jobExecution = jobLauncherTestUtils.launchJob(params);

            // Assert
            assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            List<ProductRankWeeklyJpaEntity> rankings = weeklyRankRepository
                .findByPeriodStartDateOrderByRankNumberAsc(LocalDate.of(2025, 1, 13));

            assertThat(rankings).hasSize(1);
            assertThat(rankings.get(0).getPeriodStartDate()).isEqualTo(LocalDate.of(2025, 1, 13)); // 월요일
            assertThat(rankings.get(0).getPeriodEndDate()).isEqualTo(LocalDate.of(2025, 1, 19)); // 일요일
        }
    }

    @Nested
    @DisplayName("멱등성 보장")
    class Idempotency {

        @Test
        @DisplayName("동일 주간에 재실행하면 기존 데이터가 갱신된다")
        void rerunUpdatesExistingData() throws Exception {
            // Arrange - 첫 번째 실행
            insertDailyMetrics(100L, LocalDate.of(2025, 1, 6), 10, 5, 2, BigDecimal.valueOf(3.0));

            JobParameters params1 = new JobParametersBuilder()
                .addString("targetDate", "20250106")
                .addLong("runId", 1L)
                .toJobParameters();

            jobLauncherTestUtils.launchJob(params1);

            // 데이터 추가
            insertDailyMetrics(200L, LocalDate.of(2025, 1, 7), 50, 20, 10, BigDecimal.valueOf(15.0));

            // Act - 두 번째 실행
            JobParameters params2 = new JobParametersBuilder()
                .addString("targetDate", "20250106")
                .addLong("runId", 2L)
                .toJobParameters();

            JobExecution jobExecution = jobLauncherTestUtils.launchJob(params2);

            // Assert
            assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            List<ProductRankWeeklyJpaEntity> rankings = weeklyRankRepository
                .findByPeriodStartDateOrderByRankNumberAsc(LocalDate.of(2025, 1, 6));

            // 중복 없이 2건만 존재
            assertThat(rankings).hasSize(2);

            // 순위가 재계산됨 (200이 1위)
            assertThat(rankings.get(0).getProductId()).isEqualTo(200L);
            assertThat(rankings.get(0).getRankNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("데이터가 없는 주간에 실행하면 빈 결과를 반환한다")
        void emptyWeekReturnsNoData() throws Exception {
            // Arrange - 다른 주간에만 데이터 존재
            insertDailyMetrics(100L, LocalDate.of(2025, 1, 20), 10, 5, 2, BigDecimal.valueOf(3.0));

            JobParameters params = new JobParametersBuilder()
                .addString("targetDate", "20250106") // 1월 6일 주간에는 데이터 없음
                .addLong("runId", System.currentTimeMillis()) // 고유 파라미터 추가
                .toJobParameters();

            // Act
            JobExecution jobExecution = jobLauncherTestUtils.launchJob(params);

            // Assert
            assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            List<ProductRankWeeklyJpaEntity> rankings = weeklyRankRepository
                .findByPeriodStartDateOrderByRankNumberAsc(LocalDate.of(2025, 1, 6));

            assertThat(rankings).isEmpty();
        }
    }

    @Nested
    @DisplayName("TOP 100 제한")
    class Top100Limit {

        @Test
        @DisplayName("100개 초과 상품이 있어도 TOP 100만 저장된다")
        void limitsTo100Products() throws Exception {
            // Arrange - 150개 상품 데이터 생성
            for (long productId = 1; productId <= 150; productId++) {
                insertDailyMetrics(
                    productId,
                    LocalDate.of(2025, 1, 6),
                    (int) productId * 10,
                    (int) productId * 5,
                    (int) productId,
                    BigDecimal.valueOf(productId * 0.1)
                );
            }

            JobParameters params = new JobParametersBuilder()
                .addString("targetDate", "20250106")
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

            // Act
            JobExecution jobExecution = jobLauncherTestUtils.launchJob(params);

            // Assert
            assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

            List<ProductRankWeeklyJpaEntity> rankings = weeklyRankRepository
                .findByPeriodStartDateOrderByRankNumberAsc(LocalDate.of(2025, 1, 6));

            assertThat(rankings).hasSize(100);

            // 가장 높은 점수의 상품이 1위 (productId 150)
            assertThat(rankings.get(0).getProductId()).isEqualTo(150L);
            assertThat(rankings.get(0).getRankNumber()).isEqualTo(1);

            // 100위는 productId 51
            assertThat(rankings.get(99).getProductId()).isEqualTo(51L);
            assertThat(rankings.get(99).getRankNumber()).isEqualTo(100);
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