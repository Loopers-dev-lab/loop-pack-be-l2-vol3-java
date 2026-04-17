package com.loopers.job.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.ranking.ProductRankingWeekly;
import com.loopers.infrastructure.metrics.persistence.ProductMetricsJpaRepository;
import com.loopers.infrastructure.ranking.persistence.ProductRankingWeeklyJpaRepository;
import com.loopers.utils.DatabaseCleanUp;

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
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Autowired
    private ProductRankingWeeklyJpaRepository productRankingWeeklyJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("weeklyRankingJob을 실행할 때,")
    @Nested
    class RunJob {

        @DisplayName("date 파라미터가 없으면, Job이 실패한다.")
        @Test
        void failsJob_whenDateParameterIsMissing() throws Exception {
            // arrange
            jobLauncherTestUtils.setJob(job);

            // act
            var jobParameters = new JobParametersBuilder()
                    .addLong("run.id", System.nanoTime())
                    .toJobParameters();
            var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

            // assert
            assertThat(jobExecution.getExitStatus().getExitCode())
                    .isEqualTo(ExitStatus.FAILED.getExitCode());
        }

        @DisplayName("7일치 product_metrics를 집계하여 주간 랭킹을 저장한다.")
        @Test
        void aggregatesWeeklyRanking() throws Exception {
            // arrange
            jobLauncherTestUtils.setJob(job);

            saveMetrics(1L, LocalDate.of(2026, 4, 7), 100L, 10L, 5L);
            saveMetrics(1L, LocalDate.of(2026, 4, 8), 200L, 20L, 10L);
            saveMetrics(2L, LocalDate.of(2026, 4, 9), 50L, 5L, 3L);

            var jobParameters = new JobParametersBuilder()
                    .addString("date", "20260413")
                    .toJobParameters();

            // act
            var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

            // assert
            List<ProductRankingWeekly> rankings = productRankingWeeklyJpaRepository.findAll();

            assertAll(
                    () -> assertThat(jobExecution.getExitStatus().getExitCode())
                            .isEqualTo(ExitStatus.COMPLETED.getExitCode()),
                    () -> assertThat(rankings).hasSize(2),
                    () -> {
                        // product 1: (100+200)*0.1 + (10+20)*0.2 + (5+10)*0.7 = 30 + 6 + 10.5 = 46.5
                        ProductRankingWeekly product1 = rankings.stream()
                                .filter(r -> r.getProductId().equals(1L))
                                .findFirst().orElseThrow();
                        assertThat(product1.getScore()).isEqualTo(46.5);
                        assertThat(product1.getScoreDate()).isEqualTo(LocalDate.of(2026, 4, 13));
                    },
                    () -> {
                        // product 2: 50*0.1 + 5*0.2 + 3*0.7 = 5 + 1 + 2.1 = 8.1
                        ProductRankingWeekly product2 = rankings.stream()
                                .filter(r -> r.getProductId().equals(2L))
                                .findFirst().orElseThrow();
                        assertThat(product2.getScore()).isEqualTo(8.1);
                    }
            );
        }

        @DisplayName("같은 파라미터로 재실행해도 결과가 동일하다 (멱등성).")
        @Test
        void isIdempotent_whenRerunWithSameParameters() throws Exception {
            // arrange
            jobLauncherTestUtils.setJob(job);

            saveMetrics(1L, LocalDate.of(2026, 4, 10), 100L, 10L, 5L);

            var jobParameters1 = new JobParametersBuilder()
                    .addString("date", "20260413")
                    .addLong("run.id", 1L)
                    .toJobParameters();
            var jobParameters2 = new JobParametersBuilder()
                    .addString("date", "20260413")
                    .addLong("run.id", 2L)
                    .toJobParameters();

            // act
            jobLauncherTestUtils.launchJob(jobParameters1);
            jobLauncherTestUtils.launchJob(jobParameters2);

            // assert
            List<ProductRankingWeekly> rankings = productRankingWeeklyJpaRepository.findAll();
            assertAll(
                    () -> assertThat(rankings).hasSize(1),
                    () -> assertThat(rankings.get(0).getProductId()).isEqualTo(1L)
            );
        }
    }

    private void saveMetrics(Long productId, LocalDate metricDate,
            Long viewCount, Long likeCount, Long orderCount) {
        try {
            var constructor = ProductMetrics.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ProductMetrics metrics = constructor.newInstance();
            ReflectionTestUtils.setField(metrics, "productId", productId);
            ReflectionTestUtils.setField(metrics, "metricDate", metricDate);
            ReflectionTestUtils.setField(metrics, "viewCount", viewCount);
            ReflectionTestUtils.setField(metrics, "likeCount", likeCount);
            ReflectionTestUtils.setField(metrics, "orderCount", orderCount);
            productMetricsJpaRepository.save(metrics);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
