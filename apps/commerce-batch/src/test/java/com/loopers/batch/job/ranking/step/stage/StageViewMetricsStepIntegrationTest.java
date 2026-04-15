package com.loopers.batch.job.ranking.step.stage;

import com.loopers.batch.job.ranking.RollingRankingJobConfig;
import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.domain.ranking.staging.StagingRankingAggregationRepository;
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
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@Import(MySqlTestContainersConfig.class)
@TestPropertySource(properties = "spring.batch.job.name=" + RollingRankingJobConfig.JOB_NAME)
class StageViewMetricsStepIntegrationTest {

    private static final String ANCHOR = "20260414";
    // anchor = 2026-04-14 → last7dStart = 2026-04-08, last30dStart = 2026-03-16, end = 2026-04-15T00:00
    private static final LocalDateTime IN_7D          = LocalDateTime.of(2026, 4, 10, 12, 0);
    private static final LocalDateTime IN_30D_ONLY    = LocalDateTime.of(2026, 3, 20, 9, 0);
    private static final LocalDateTime BEFORE_30D     = LocalDateTime.of(2026, 3, 10, 0, 0);   // 제외
    private static final LocalDateTime ON_TODAY       = LocalDateTime.of(2026, 4, 15, 0, 0);   // 오늘 = 제외

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired @Qualifier(RollingRankingJobConfig.JOB_NAME) private Job job;
    @Autowired private StagingRankingAggregationRepository aggregationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("anchor 범위 내 bucket 은 집계되고, 범위 밖 (30일 이전·오늘 이후) 은 제외된다.")
    @Test
    void aggregatesOnlyWithinWindow() throws Exception {
        // product 1: 7d 10 + 30d 추가 5 = sum7d 10, sum30d 15
        saveView(1L, IN_7D, 10L);
        saveView(1L, IN_30D_ONLY, 5L);
        // product 2: 30d only
        saveView(2L, IN_30D_ONLY, 7L);
        // 범위 밖 — 집계 제외
        saveView(3L, BEFORE_30D, 100L);
        saveView(3L, ON_TODAY, 100L);

        jobLauncherTestUtils.setJob(job);
        JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR));

        assertAll(
                () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                () -> assertThat(viewCount("LAST_7D",  ANCHOR, 1L)).isEqualTo(10L),
                () -> assertThat(viewCount("LAST_30D", ANCHOR, 1L)).isEqualTo(15L),
                () -> assertThat(viewCount("LAST_7D",  ANCHOR, 2L)).isEqualTo(0L),
                () -> assertThat(viewCount("LAST_30D", ANCHOR, 2L)).isEqualTo(7L),
                () -> assertThat(productExists(ANCHOR, 3L)).isFalse(),
                () -> assertThat(aggregationRepository.countByPeriodKey(ANCHOR)).isEqualTo(4L)  // (LAST_7D + LAST_30D) × 2 products
        );
    }

    @DisplayName("같은 anchor 로 Job 을 다시 돌려도 결과가 동일하다 (멱등성).")
    @Test
    void idempotentOnRerun() throws Exception {
        saveView(1L, IN_7D, 3L);
        saveView(1L, IN_7D.plusHours(1), 4L);

        jobLauncherTestUtils.setJob(job);
        JobExecution first  = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR));
        JobExecution second = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR));

        assertAll(
                () -> assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                () -> assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                () -> assertThat(viewCount("LAST_7D",  ANCHOR, 1L)).isEqualTo(7L),
                () -> assertThat(viewCount("LAST_30D", ANCHOR, 1L)).isEqualTo(7L),
                () -> assertThat(aggregationRepository.countByPeriodKey(ANCHOR)).isEqualTo(2L)
        );
    }

    @DisplayName("원천이 비어 있어도 Job 은 성공하고 staging 은 비어 있다.")
    @Test
    void emptySourceSucceeds() throws Exception {
        jobLauncherTestUtils.setJob(job);
        JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR));

        assertAll(
                () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                () -> assertThat(aggregationRepository.countByPeriodKey(ANCHOR)).isZero()
        );
    }

    // -- helpers --

    private void saveView(long productId, LocalDateTime bucketTime, long viewCount) {
        jdbcTemplate.update(
                "INSERT INTO product_view_metrics (product_id, bucket_time, view_count) VALUES (?, ?, ?)",
                productId, Timestamp.valueOf(bucketTime), viewCount
        );
    }

    private long viewCount(String periodType, String periodKey, long productId) {
        Long v = jdbcTemplate.queryForObject(
                "SELECT view_count FROM staging_ranking_aggregation " +
                        " WHERE period_type=? AND period_key=? AND product_id=?",
                Long.class, periodType, periodKey, productId
        );
        return v == null ? 0L : v;
    }

    private boolean productExists(String periodKey, long productId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staging_ranking_aggregation " +
                        " WHERE period_key=? AND product_id=?",
                Integer.class, periodKey, productId
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
