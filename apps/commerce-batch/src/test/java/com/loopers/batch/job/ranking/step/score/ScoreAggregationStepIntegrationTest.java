package com.loopers.batch.job.ranking.step.score;

import com.loopers.batch.job.ranking.RollingRankingJobConfig;
import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.domain.ranking.weight.WeightConfig;
import com.loopers.domain.ranking.weight.WeightConfigRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Step 5 — 1차 staging 의 raw sum 에 score 를 계산해 2차 staging 에 적재하는 파이프라인 검증.
 * Step 0~3 으로 1차가 먼저 채워지므로, 이 테스트는 view/like/order 원천에 시드하고
 * Job 전체를 돌려 Step 5 결과만 확인한다.
 */
@SpringBootTest
@SpringBatchTest
@Import(MySqlTestContainersConfig.class)
@TestPropertySource(properties = "spring.batch.job.name=" + RollingRankingJobConfig.JOB_NAME)
class ScoreAggregationStepIntegrationTest {

    private static final String ANCHOR = "20260414";
    private static final LocalDateTime IN_7D = LocalDateTime.of(2026, 4, 10, 12, 0);

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired @Qualifier(RollingRankingJobConfig.JOB_NAME) private Job job;
    @Autowired private WeightConfigRepository weightConfigRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("활성 weight_group 별로 2차 staging 에 row 가 fan-out 되고 score 가 공식대로 계산된다.")
    @Test
    void fansOutPerWeightGroupWithCorrectScore() throws Exception {
        weightConfigRepository.save(new WeightConfig("control", 0.1, 0.2, 0.7, 100, true));

        saveView(1L, IN_7D, 100);
        saveLike(1L, IN_7D, 50);
        saveOrder(1L, IN_7D, 999);

        jobLauncherTestUtils.setJob(job);
        JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR));

        double expectedScore = ScoreFormula.compute(
                100, 50, 999,
                new WeightConfig("control", 0.1, 0.2, 0.7, 100, true)
        );

        assertAll(
                () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                // LAST_7D + LAST_30D × control 1 group = 2 rows
                () -> assertThat(scoredCount(ANCHOR)).isEqualTo(2L),
                () -> assertThat(scoreOf("LAST_7D",  ANCHOR, "control", 1L)).isCloseTo(expectedScore, offset(1e-9)),
                () -> assertThat(scoreOf("LAST_30D", ANCHOR, "control", 1L)).isCloseTo(expectedScore, offset(1e-9))
        );
    }

    @DisplayName("여러 weight_group 이 활성화되어 있으면 각 그룹별로 독립적인 score 가 저장된다.")
    @Test
    void multipleWeightGroupsProduceIndependentScores() throws Exception {
        weightConfigRepository.save(new WeightConfig("control",      0.1, 0.2, 0.7, 50, true));
        weightConfigRepository.save(new WeightConfig("experiment_a", 0.8, 0.1, 0.1, 50, true));
        weightConfigRepository.save(new WeightConfig("inactive",     0.3, 0.3, 0.4, 0, false));  // 제외

        saveView(1L, IN_7D, 100);
        saveLike(1L, IN_7D, 100);
        saveOrder(1L, IN_7D, 999);

        jobLauncherTestUtils.setJob(job);
        JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR));

        assertAll(
                () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                // (LAST_7D + LAST_30D) × (control + experiment_a) = 4 rows, inactive 제외
                () -> assertThat(scoredCount(ANCHOR)).isEqualTo(4L),
                () -> assertThat(scoreOf("LAST_7D", ANCHOR, "control", 1L))
                        .isNotEqualTo(scoreOf("LAST_7D", ANCHOR, "experiment_a", 1L)),
                () -> assertThat(existsScored(ANCHOR, "inactive")).isFalse()
        );
    }

    @DisplayName("원천이 비어 있으면 1차/2차 staging 모두 비어 있고 Job 은 성공한다.")
    @Test
    void emptySourceProducesEmptyScored() throws Exception {
        weightConfigRepository.save(new WeightConfig("control", 0.1, 0.2, 0.7, 100, true));

        jobLauncherTestUtils.setJob(job);
        JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR));

        assertAll(
                () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                () -> assertThat(scoredCount(ANCHOR)).isZero()
        );
    }

    // -- helpers --

    private void saveView(long productId, LocalDateTime bucketTime, long viewCount) {
        jdbcTemplate.update(
                "INSERT INTO product_view_metrics (product_id, bucket_time, view_count) VALUES (?, ?, ?)",
                productId, Timestamp.valueOf(bucketTime), viewCount
        );
    }

    private void saveLike(long productId, LocalDateTime bucketTime, long likeCount) {
        jdbcTemplate.update(
                "INSERT INTO product_like_metrics (product_id, bucket_time, like_count) VALUES (?, ?, ?)",
                productId, Timestamp.valueOf(bucketTime), likeCount
        );
    }

    private void saveOrder(long productId, LocalDateTime bucketTime, long salesAmount) {
        jdbcTemplate.update(
                "INSERT INTO product_order_metrics (product_id, bucket_time, order_count, quantity, sales_amount) " +
                        "VALUES (?, ?, 1, 1, ?)",
                productId, Timestamp.valueOf(bucketTime), salesAmount
        );
    }

    private long scoredCount(String periodKey) {
        Long c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staging_ranking_scored WHERE period_key = ?",
                Long.class, periodKey
        );
        return c == null ? 0L : c;
    }

    private double scoreOf(String periodType, String periodKey, String group, long productId) {
        Double s = jdbcTemplate.queryForObject(
                "SELECT score FROM staging_ranking_scored " +
                        " WHERE period_type=? AND period_key=? AND weight_group=? AND product_id=?",
                Double.class, periodType, periodKey, group, productId
        );
        return s == null ? 0.0 : s;
    }

    private boolean existsScored(String periodKey, String group) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staging_ranking_scored WHERE period_key=? AND weight_group=?",
                Integer.class, periodKey, group
        );
        return c != null && c > 0;
    }

    private JobParameters paramsOf(String anchorDate) {
        return new JobParametersBuilder()
                .addString(RankingJobParametersListener.PARAM_ANCHOR_DATE, anchorDate)
                .addLong("runTimestamp", System.nanoTime())
                .toJobParameters();
    }
}
