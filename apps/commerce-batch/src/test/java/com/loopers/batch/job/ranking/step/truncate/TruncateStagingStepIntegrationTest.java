package com.loopers.batch.job.ranking.step.truncate;

import com.loopers.batch.job.ranking.RollingRankingJobConfig;
import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.domain.ranking.staging.StagingRankingAggregation;
import com.loopers.domain.ranking.staging.StagingRankingAggregationRepository;
import com.loopers.domain.ranking.staging.StagingRankingScored;
import com.loopers.domain.ranking.staging.StagingRankingScoredRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@Import(MySqlTestContainersConfig.class)
@TestPropertySource(properties = "spring.batch.job.name=" + RollingRankingJobConfig.JOB_NAME)
class TruncateStagingStepIntegrationTest {

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired @Qualifier(RollingRankingJobConfig.JOB_NAME) private Job job;
    @Autowired private StagingRankingAggregationRepository aggregationRepository;
    @Autowired private StagingRankingScoredRepository scoredRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("anchorDateKey 에 해당하는 두 스테이징 테이블의 row 만 삭제된다.")
    @Test
    void deletesOnlyTargetAnchor() throws Exception {
        String targetAnchor = "20260414";
        String otherAnchor = "20260101";
        aggregationRepository.save(new StagingRankingAggregation("LAST_7D",  targetAnchor, 1L, 10, 0, 0));
        aggregationRepository.save(new StagingRankingAggregation("LAST_30D", targetAnchor, 2L, 20, 0, 0));
        aggregationRepository.save(new StagingRankingAggregation("LAST_7D",  otherAnchor,  3L, 30, 0, 0));
        scoredRepository.save(new StagingRankingScored("LAST_7D",  targetAnchor, "control", 1L, 10, 0, 0, 1.0));
        scoredRepository.save(new StagingRankingScored("LAST_30D", otherAnchor,  "control", 3L, 30, 0, 0, 3.0));

        jobLauncherTestUtils.setJob(job);
        JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf(targetAnchor));

        assertAll(
                () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                () -> assertThat(aggregationRepository.countByPeriodKey(targetAnchor)).isZero(),
                () -> assertThat(scoredRepository.countByPeriodKey(targetAnchor)).isZero(),
                () -> assertThat(aggregationRepository.countByPeriodKey(otherAnchor)).isOne(),
                () -> assertThat(scoredRepository.countByPeriodKey(otherAnchor)).isOne()
        );
    }

    @DisplayName("비어있는 스테이징에 실행해도 멱등하게 성공한다 (첫 실행 시나리오).")
    @Test
    void succeedsOnEmptyStaging() throws Exception {
        jobLauncherTestUtils.setJob(job);
        JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf("20260414"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @DisplayName("같은 anchorDate 로 두 번 돌려도 결과가 동일하다 (배치 멱등성).")
    @Test
    void idempotentOnRepeatedRun() throws Exception {
        String anchor = "20260414";
        aggregationRepository.save(new StagingRankingAggregation("LAST_7D", anchor, 1L, 10, 0, 0));
        scoredRepository.save(new StagingRankingScored("LAST_7D", anchor, "control", 1L, 10, 0, 0, 1.0));

        jobLauncherTestUtils.setJob(job);

        JobExecution first = jobLauncherTestUtils.launchJob(paramsOf(anchor));
        // 재실행을 위해 새 JobInstance 로 실행 (runTimestamp 로 격리)
        JobExecution second = jobLauncherTestUtils.launchJob(paramsOf(anchor));

        assertAll(
                () -> assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                () -> assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                () -> assertThat(aggregationRepository.countByPeriodKey(anchor)).isZero(),
                () -> assertThat(scoredRepository.countByPeriodKey(anchor)).isZero()
        );
    }

    @DisplayName("anchorDate 파라미터가 없으면 Job 이 실패한다.")
    @Test
    void failsWhenAnchorDateMissing() throws Exception {
        jobLauncherTestUtils.setJob(job);

        JobExecution execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addLong("runTimestamp", System.nanoTime())
                        .toJobParameters()
        );

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    private JobParameters paramsOf(String anchorDate) {
        return new JobParametersBuilder()
                .addString(RankingJobParametersListener.PARAM_ANCHOR_DATE, anchorDate)
                .addLong("runTimestamp", System.nanoTime())
                .toJobParameters();
    }
}
