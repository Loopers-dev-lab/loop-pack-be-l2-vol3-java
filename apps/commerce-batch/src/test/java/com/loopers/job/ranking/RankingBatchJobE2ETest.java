package com.loopers.job.ranking;

import com.loopers.batch.ranking.job.RankingBatchJobConfig;
import com.loopers.domain.ranking.batch.RankingBatchJobParameters;
import com.loopers.infrastructure.ranking.batch.MvProductRankStagingJpaRepository;
import com.loopers.infrastructure.ranking.batch.ProductMetricsEntity;
import com.loopers.infrastructure.ranking.batch.ProductMetricsJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

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

    @Autowired
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Autowired
    private MvProductRankStagingJpaRepository mvProductRankStagingJpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDb() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            mvProductRankStagingJpaRepository.deleteAllInBatch();
            productMetricsJpaRepository.deleteAllInBatch();
        });
    }

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
        Instant at = Instant.parse("2026-04-10T00:00:00Z");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            productMetricsJpaRepository.save(ProductMetricsEntity.forRankingRead(1L, 0L, 0L, 10L, at));
            productMetricsJpaRepository.save(ProductMetricsEntity.forRankingRead(2L, 0L, 0L, 5L, at));
        });
        var execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString(RankingBatchJobParameters.JOB_PARAM_PERIOD, "WEEKLY")
                        .addString(RankingBatchJobParameters.JOB_PARAM_PERIOD_KEY, "2026W15")
                        .addLong("run.id", System.currentTimeMillis())
                        .toJobParameters()
        );

        var staging = mvProductRankStagingJpaRepository.findByPeriodTypeAndPeriodKeyOrderByRankValueAsc(
                "WEEKLY",
                "2026W15"
        );

        assertAll(
                () -> assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
                () -> assertThat(execution.getStepExecutions()).hasSize(4),
                () -> assertThat(staging).hasSize(2),
                () -> assertThat(staging.get(0).getProductId()).isEqualTo(1L),
                () -> assertThat(staging.get(0).getRankValue()).isEqualTo(1),
                () -> assertThat(staging.get(1).getProductId()).isEqualTo(2L)
        );
    }
}
