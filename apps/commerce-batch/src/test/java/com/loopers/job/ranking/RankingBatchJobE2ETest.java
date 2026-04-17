package com.loopers.job.ranking;

import com.loopers.batch.ranking.job.RankingBatchJobConfig;
import com.loopers.domain.ranking.batch.RankingBatchJobParameters;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "outbox.relay.enabled=false"
})
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + RankingBatchJobConfig.JOB_NAME)
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class RankingBatchJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(RankingBatchJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("period 없이 실행하면 JobParameters 검증에서 실패한다.")
    void launchJob_whenPeriodMissing_shouldFailValidation() {
        jobLauncherTestUtils.setJob(job);

        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString(RankingBatchJobParameters.JOB_PARAM_PERIOD_KEY, "2026W15")
                        .addLong("run.id", System.currentTimeMillis())
                        .toJobParameters()
        )).isInstanceOf(JobParametersInvalidException.class);
    }

    @Test
    @DisplayName("유효한 period·periodKey면 Job이 COMPLETED 된다.")
    void launchJob_whenValidParameters_shouldComplete() throws Exception {
        jobLauncherTestUtils.setJob(job);
        var execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString(RankingBatchJobParameters.JOB_PARAM_PERIOD, "WEEKLY")
                        .addString(RankingBatchJobParameters.JOB_PARAM_PERIOD_KEY, "2026W15")
                        .addLong("run.id", System.currentTimeMillis())
                        .toJobParameters()
        );

        assertAll(
                () -> assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
                () -> assertThat(execution.getStepExecutions()).hasSize(4)
        );
    }
}
