package com.loopers.batch.job.ranking.step.purge;

import com.loopers.batch.job.ranking.RollingRankingJobConfig;
import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.domain.ranking.mv.MvProductRankLast30d;
import com.loopers.domain.ranking.mv.MvProductRankLast30dRepository;
import com.loopers.domain.ranking.mv.MvProductRankLast7d;
import com.loopers.domain.ranking.mv.MvProductRankLast7dRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@Import(MySqlTestContainersConfig.class)
@TestPropertySource(properties = "spring.batch.job.name=" + RollingRankingJobConfig.JOB_NAME)
class PurgeMvStepIntegrationTest {

    private static final LocalDate TARGET_ANCHOR = LocalDate.of(2026, 4, 14);
    private static final LocalDate OTHER_ANCHOR  = LocalDate.of(2026, 4, 13);
    private static final String ANCHOR_KEY = "20260414";
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 4, 15, 1, 0);

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired @Qualifier(RollingRankingJobConfig.JOB_NAME) private Job job;
    @Autowired private MvProductRankLast7dRepository last7dRepository;
    @Autowired private MvProductRankLast30dRepository last30dRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("타겟 anchorDate 의 MV row 만 양쪽 테이블에서 삭제되고 다른 anchor 는 유지된다.")
    @Test
    void purgesOnlyTargetAnchorInBothMvTables() throws Exception {
        last7dRepository.save(mv7d(TARGET_ANCHOR, 1L, 1));
        last7dRepository.save(mv7d(OTHER_ANCHOR, 2L, 1));
        last30dRepository.save(mv30d(TARGET_ANCHOR, 1L, 1));
        last30dRepository.save(mv30d(OTHER_ANCHOR, 2L, 1));

        jobLauncherTestUtils.setJob(job);
        JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR_KEY));

        assertAll(
                () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                () -> assertThat(last7dRepository.countByAnchorDate(TARGET_ANCHOR)).isZero(),
                () -> assertThat(last30dRepository.countByAnchorDate(TARGET_ANCHOR)).isZero(),
                () -> assertThat(last7dRepository.countByAnchorDate(OTHER_ANCHOR)).isOne(),
                () -> assertThat(last30dRepository.countByAnchorDate(OTHER_ANCHOR)).isOne()
        );
    }

    @DisplayName("MV 에 동일 anchor/같은 상품이 여러 weight_group 에 있으면 그룹 구분 없이 전부 삭제된다.")
    @Test
    void purgesAllWeightGroupsForAnchor() throws Exception {
        last7dRepository.save(mv7d(TARGET_ANCHOR, "control",      1L, 1));
        last7dRepository.save(mv7d(TARGET_ANCHOR, "experiment_a", 1L, 1));
        last7dRepository.save(mv7d(TARGET_ANCHOR, "experiment_b", 1L, 1));

        jobLauncherTestUtils.setJob(job);
        JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR_KEY));

        assertAll(
                () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                () -> assertThat(last7dRepository.countByAnchorDate(TARGET_ANCHOR)).isZero()
        );
    }

    @DisplayName("MV 가 비어 있어도 Job 은 성공한다 (첫 실행 시나리오).")
    @Test
    void succeedsOnEmptyMv() throws Exception {
        jobLauncherTestUtils.setJob(job);
        JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR_KEY));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    // -- helpers --

    private static MvProductRankLast7d mv7d(LocalDate anchor, long productId, int rank) {
        return mv7d(anchor, "control", productId, rank);
    }

    private static MvProductRankLast7d mv7d(LocalDate anchor, String group, long productId, int rank) {
        return new MvProductRankLast7d(anchor, group, productId, 0, 0, 0, 0.0, rank, CREATED);
    }

    private static MvProductRankLast30d mv30d(LocalDate anchor, long productId, int rank) {
        return new MvProductRankLast30d(anchor, "control", productId, 0, 0, 0, 0.0, rank, CREATED);
    }

    private JobParameters paramsOf(String anchorDate) {
        return new JobParametersBuilder()
                .addString(RankingJobParametersListener.PARAM_ANCHOR_DATE, anchorDate)
                .addLong("runTimestamp", System.nanoTime())
                .toJobParameters();
    }
}
