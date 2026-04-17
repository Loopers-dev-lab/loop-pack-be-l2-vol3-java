package com.loopers.job.ranking;

import com.loopers.batch.ranking.job.RankingBatchJobConfig;
import com.loopers.domain.ranking.batch.RankingBatchJobParameters;
import com.loopers.infrastructure.ranking.batch.MvProductRankStagingJpaRepository;
import com.loopers.infrastructure.ranking.mv.MvProductRankWeeklyJpaRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

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

    private static final List<String> TRUNCATE_TABLES = List.of(
            "mv_product_rank_staging",
            "mv_product_rank_weekly",
            "mv_product_rank_monthly",
            "product_metrics",
            "outbox_event"
    );

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(RankingBatchJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private MvProductRankStagingJpaRepository mvProductRankStagingJpaRepository;

    @Autowired
    private MvProductRankWeeklyJpaRepository mvProductRankWeeklyJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : TRUNCATE_TABLES) {
            jdbcTemplate.execute("TRUNCATE TABLE `" + table + "`");
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
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
        jdbcTemplate.update(
                """
                INSERT INTO product_metrics (product_id, like_count, view_count, sold_quantity, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                1L, 0L, 0L, 10L, java.sql.Timestamp.from(at)
        );
        jdbcTemplate.update(
                """
                INSERT INTO product_metrics (product_id, like_count, view_count, sold_quantity, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                2L, 0L, 0L, 5L, java.sql.Timestamp.from(at)
        );
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
        var weeklyMv = mvProductRankWeeklyJpaRepository.findByPeriodKeyOrderByRankValueAsc("2026W15");

        assertAll(
                () -> assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
                () -> assertThat(execution.getStepExecutions()).hasSize(4),
                () -> assertThat(staging).hasSize(2),
                () -> assertThat(staging.get(0).getProductId()).isEqualTo(1L),
                () -> assertThat(staging.get(0).getRankValue()).isEqualTo(1),
                () -> assertThat(staging.get(1).getProductId()).isEqualTo(2L),
                () -> assertThat(weeklyMv).hasSize(2),
                () -> assertThat(weeklyMv.get(0).getProductId()).isEqualTo(1L),
                () -> assertThat(weeklyMv.get(1).getProductId()).isEqualTo(2L)
        );
    }

    @Test
    @DisplayName("동일 period·periodKey로 Job을 두 번 실행해도 MV rank·product_id 결과가 동일하다(멱등).")
    void launchJob_twiceSamePeriod_shouldProduceSameWeeklyMv() throws Exception {
        jobLauncherTestUtils.setJob(job);
        Instant at = Instant.parse("2026-04-10T00:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO product_metrics (product_id, like_count, view_count, sold_quantity, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                1L, 0L, 0L, 10L, java.sql.Timestamp.from(at)
        );
        jdbcTemplate.update(
                """
                INSERT INTO product_metrics (product_id, like_count, view_count, sold_quantity, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                2L, 0L, 0L, 5L, java.sql.Timestamp.from(at)
        );
        var params1 = new JobParametersBuilder()
                .addString(RankingBatchJobParameters.JOB_PARAM_PERIOD, "WEEKLY")
                .addString(RankingBatchJobParameters.JOB_PARAM_PERIOD_KEY, "2026W15")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        var first = jobLauncherTestUtils.launchJob(params1);
        assertThat(first.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        var afterFirst = mvProductRankWeeklyJpaRepository.findByPeriodKeyOrderByRankValueAsc("2026W15");

        var params2 = new JobParametersBuilder()
                .addString(RankingBatchJobParameters.JOB_PARAM_PERIOD, "WEEKLY")
                .addString(RankingBatchJobParameters.JOB_PARAM_PERIOD_KEY, "2026W15")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        var second = jobLauncherTestUtils.launchJob(params2);
        assertThat(second.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        var afterSecond = mvProductRankWeeklyJpaRepository.findByPeriodKeyOrderByRankValueAsc("2026W15");

        assertAll(
                () -> assertThat(afterSecond).hasSize(afterFirst.size()),
                () -> assertThat(afterSecond.get(0).getProductId()).isEqualTo(afterFirst.get(0).getProductId()),
                () -> assertThat(afterSecond.get(0).getRankValue()).isEqualTo(afterFirst.get(0).getRankValue()),
                () -> assertThat(afterSecond.get(0).getScore()).isEqualByComparingTo(afterFirst.get(0).getScore()),
                () -> assertThat(afterSecond.get(1).getProductId()).isEqualTo(afterFirst.get(1).getProductId()),
                () -> assertThat(afterSecond.get(1).getRankValue()).isEqualTo(afterFirst.get(1).getRankValue()),
                () -> assertThat(afterSecond.get(1).getScore()).isEqualByComparingTo(afterFirst.get(1).getScore())
        );
    }
}
