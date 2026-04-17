package com.loopers.batch.job.ranking.step.stage;

import com.loopers.batch.job.ranking.RollingRankingJobConfig;
import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.domain.ranking.staging.StagingRankingAggregationRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
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
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Step 1 (View) + Step 2 (Like) + Step 3 (Order) 의 파이프라인 검증.
 * 서로 다른 메트릭의 UPSERT 가 같은 staging row 에 올바르게 합쳐지는지,
 * 한 메트릭만 있는 상품도 정상 처리되는지 확인한다.
 */
@SpringBootTest
@SpringBatchTest
@Import(MySqlTestContainersConfig.class)
@TestPropertySource(properties = "spring.batch.job.name=" + RollingRankingJobConfig.JOB_NAME)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StageMetricsPipelineIntegrationTest {

    private static final String ANCHOR = "20260414";
    private static final LocalDateTime IN_7D       = LocalDateTime.of(2026, 4, 10, 12, 0);
    private static final LocalDateTime IN_30D_ONLY = LocalDateTime.of(2026, 3, 20, 9, 0);

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired @Qualifier(RollingRankingJobConfig.JOB_NAME) private Job job;
    @Autowired private StagingRankingAggregationRepository aggregationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 메트릭_병합 {

        @Test
        void 세_메트릭이_모두_있는_상품은_staging_row_하나에_세_컬럼이_모두_채워진다() throws Exception {
            saveView(1L, IN_7D, 10);
            saveView(1L, IN_30D_ONLY, 5);
            saveLike(1L, IN_7D, 2);
            saveLike(1L, IN_30D_ONLY, 3);
            saveOrder(1L, IN_7D, 1000);
            saveOrder(1L, IN_30D_ONLY, 2000);

            jobLauncherTestUtils.setJob(job);
            JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR));

            assertAll(
                    () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                    () -> assertThat(row("LAST_7D",  ANCHOR, 1L)).containsExactly(10L, 2L, 1000L),
                    () -> assertThat(row("LAST_30D", ANCHOR, 1L)).containsExactly(15L, 5L, 3000L)
            );
        }

        @Test
        void Like_만_있는_상품은_Step2_의_INSERT_로_row_가_생성되고_view_sales_는_0이다() throws Exception {
            saveLike(2L, IN_7D, 4);

            jobLauncherTestUtils.setJob(job);
            JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR));

            assertAll(
                    () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                    () -> assertThat(row("LAST_7D",  ANCHOR, 2L)).containsExactly(0L, 4L, 0L),
                    () -> assertThat(row("LAST_30D", ANCHOR, 2L)).containsExactly(0L, 4L, 0L)
            );
        }

        @Test
        void Order_만_있는_상품도_Step3_의_INSERT_로_row_가_생성된다() throws Exception {
            saveOrder(3L, IN_30D_ONLY, 5000);

            jobLauncherTestUtils.setJob(job);
            JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR));

            assertAll(
                    () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                    () -> assertThat(row("LAST_7D",  ANCHOR, 3L)).containsExactly(0L, 0L, 0L),
                    () -> assertThat(row("LAST_30D", ANCHOR, 3L)).containsExactly(0L, 0L, 5000L)
            );
        }
    }

    @Nested
    class 독립_적재 {

        @Test
        void 메트릭마다_다른_상품_집합이_있어도_각각_독립적으로_적재된다() throws Exception {
            saveView(10L, IN_7D, 1);
            saveLike(20L, IN_7D, 2);
            saveOrder(30L, IN_7D, 3);

            jobLauncherTestUtils.setJob(job);
            JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR));

            assertAll(
                    () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                    () -> assertThat(row("LAST_7D", ANCHOR, 10L)).containsExactly(1L, 0L, 0L),
                    () -> assertThat(row("LAST_7D", ANCHOR, 20L)).containsExactly(0L, 2L, 0L),
                    () -> assertThat(row("LAST_7D", ANCHOR, 30L)).containsExactly(0L, 0L, 3L),
                    // product 당 LAST_7D + LAST_30D → 3 × 2 = 6
                    () -> assertThat(aggregationRepository.countByPeriodKey(ANCHOR)).isEqualTo(6L)
            );
        }
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

    /** (view_count, like_count, sales_amount) 을 배열로 반환. */
    private Long[] row(String periodType, String periodKey, long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT view_count, like_count, sales_amount FROM staging_ranking_aggregation " +
                        " WHERE period_type=? AND period_key=? AND product_id=?",
                (rs, rn) -> new Long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)},
                periodType, periodKey, productId
        );
    }

    private JobParameters paramsOf(String anchorDate) {
        return new JobParametersBuilder()
                .addString(RankingJobParametersListener.PARAM_ANCHOR_DATE, anchorDate)
                .addLong("runTimestamp", System.nanoTime())
                .toJobParameters();
    }
}
