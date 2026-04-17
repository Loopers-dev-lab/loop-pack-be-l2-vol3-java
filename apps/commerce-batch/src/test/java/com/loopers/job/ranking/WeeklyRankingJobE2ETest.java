package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;
import com.loopers.domain.metrics.ProductMetricsDaily;
import com.loopers.domain.ranking.ProductRankWeekly;
import com.loopers.infrastructure.metrics.ProductMetricsDailyBatchRepository;
import com.loopers.infrastructure.ranking.ProductRankWeeklyJpaRepository;
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
@TestPropertySource(properties = "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME)
class WeeklyRankingJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(WeeklyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private ProductMetricsDailyBatchRepository metricsRepository;

    @Autowired
    private ProductRankWeeklyJpaRepository weeklyRepository;

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

    @DisplayName("7일치 일별 메트릭이 있을 때, 주간 랭킹이 점수 내림차순으로 생성된다")
    @Test
    void shouldCreateWeeklyRankingFromDailyMetrics() throws Exception {
        // given
        LocalDate endDate = LocalDate.of(2026, 4, 13);
        LocalDate startDate = endDate.minusDays(6);

        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);

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

        List<ProductRankWeekly> rankings = weeklyRepository.findAll();
        assertAll(
            () -> assertThat(rankings).hasSize(2),
            () -> assertThat(rankings.get(0).getRanking()).isEqualTo(1),
            () -> assertThat(rankings.get(1).getRanking()).isEqualTo(2)
        );
    }

    @DisplayName("일별 메트릭이 없으면 주간 랭킹이 생성되지 않는다")
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
        assertThat(weeklyRepository.findAll()).isEmpty();
    }
}
