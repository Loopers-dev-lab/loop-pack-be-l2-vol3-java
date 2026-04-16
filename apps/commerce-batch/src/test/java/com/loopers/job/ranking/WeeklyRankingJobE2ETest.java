package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;
import com.loopers.infrastructure.ranking.ProductRankWeeklyJpaRepository;
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
    private ProductRankWeeklyJpaRepository productRankWeeklyJpaRepository;

    @DisplayName("targetDate 파라미터 없이 실행하면 weeklyRankingJob이 실패한다.")
    @Test
    void fails_whenTargetDateParamIsMissing() throws Exception {
        // Arrange
        jobLauncherTestUtils.setJob(job);

        // Act
        var jobExecution = jobLauncherTestUtils.launchJob();

        // Assert
        assertThat(jobExecution.getExitStatus().getExitCode())
            .isEqualTo(ExitStatus.FAILED.getExitCode());
    }

    @DisplayName("targetDate 파라미터와 함께 실행하면 weeklyRankingJob이 성공한다.")
    @Test
    void success_whenTargetDateParamIsGiven() throws Exception {
        // Arrange
        jobLauncherTestUtils.setJob(job);
        var jobParameters = new JobParametersBuilder()
            .addString("targetDate", "20260416")
            .toJobParameters();

        // Act
        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // Assert
        assertAll(
            () -> assertThat(jobExecution).isNotNull(),
            () -> assertThat(jobExecution.getExitStatus().getExitCode())
                .isEqualTo(ExitStatus.COMPLETED.getExitCode())
        );
    }
}
