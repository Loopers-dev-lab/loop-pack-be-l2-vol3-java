package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.MonthlyRankingJobConfig;
import com.loopers.domain.metrics.ProductMetricsDaily;
import com.loopers.domain.ranking.ProductRankMonthly;
import com.loopers.infrastructure.metrics.ProductMetricsDailyBatchRepository;
import com.loopers.infrastructure.ranking.ProductRankMonthlyJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

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
    private ProductMetricsDailyBatchRepository metricsRepository;

    @Autowired
    private ProductRankMonthlyJpaRepository monthlyRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("30일 범위 내 일별 메트릭이 있을 때, 월간 랭킹이 점수 내림차순으로 생성된다")
    @Test
    void shouldCreateMonthlyRankingFromDailyMetrics() throws Exception {
        // given
        LocalDate endDate = LocalDate.of(2026, 4, 13);
        LocalDate day1 = endDate.minusDays(28);
        LocalDate day2 = endDate.minusDays(15);
        LocalDate day3 = endDate;

        for (LocalDate date : List.of(day1, day2, day3)) {
            var m1 = new ProductMetricsDaily(1L, date);
            for (int j = 0; j < 10; j++) m1.incrementViewCount();
            for (int j = 0; j < 2; j++) m1.incrementLikeCount();
            m1.incrementSaleCount(1);
            metricsRepository.save(m1);

            var m2 = new ProductMetricsDaily(2L, date);
            for (int j = 0; j < 5; j++) m2.incrementViewCount();
            for (int j = 0; j < 5; j++) m2.incrementLikeCount();
            m2.incrementSaleCount(3);
            metricsRepository.save(m2);
        }

        // when
        var jobParameters = new JobParametersBuilder(jobLauncherTestUtils.getUniqueJobParameters())
            .addLocalDate("requestDate", endDate)
            .toJobParameters();
        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<ProductRankMonthly> rankings = monthlyRepository.findAll();
        assertAll(
            () -> assertThat(rankings).hasSize(2),
            () -> assertThat(rankings.get(0).getRanking()).isEqualTo(1),
            () -> assertThat(rankings.get(1).getRanking()).isEqualTo(2)
        );
    }

    @DisplayName("일별 메트릭이 없으면 월간 랭킹이 생성되지 않는다")
    @Test
    void shouldCompleteWithNoRankingsWhenNoMetrics() throws Exception {
        // given
        LocalDate endDate = LocalDate.of(2026, 4, 13);

        // when
        var jobParameters = new JobParametersBuilder(jobLauncherTestUtils.getUniqueJobParameters())
            .addLocalDate("requestDate", endDate)
            .toJobParameters();
        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
        assertThat(monthlyRepository.findAll()).isEmpty();
    }
}
