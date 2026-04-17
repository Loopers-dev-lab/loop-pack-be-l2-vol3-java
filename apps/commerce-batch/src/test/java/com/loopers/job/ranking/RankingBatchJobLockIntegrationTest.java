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
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "outbox.relay.enabled=false"
})
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + RankingBatchJobConfig.JOB_NAME)
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class RankingBatchJobLockIntegrationTest {

    private static final String PERIOD = "WEEKLY";
    private static final String PERIOD_KEY = "2026W20";

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(RankingBatchJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("동일 period 락이 이미 있으면 첫 Step에서 실패한다.")
    void launchJob_whenLockAlreadyHeld_shouldFail() throws Exception {
        String lockKey = RankingBatchJobParameters.redisLockKey(PERIOD, PERIOD_KEY);
        redisTemplate.opsForValue().set(lockKey, "other-owner", Duration.ofMinutes(10));

        jobLauncherTestUtils.setJob(job);
        var execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString(RankingBatchJobParameters.JOB_PARAM_PERIOD, PERIOD)
                        .addString(RankingBatchJobParameters.JOB_PARAM_PERIOD_KEY, PERIOD_KEY)
                        .addLong("run.id", System.currentTimeMillis())
                        .toJobParameters()
        );

        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
        assertThat(execution.getStepExecutions()).hasSize(1);
    }
}
