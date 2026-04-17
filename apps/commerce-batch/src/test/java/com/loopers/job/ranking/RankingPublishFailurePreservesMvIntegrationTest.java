package com.loopers.job.ranking;

import com.loopers.batch.ranking.job.RankingBatchJobConfig;
import com.loopers.domain.ranking.batch.RankingBatchJobParameters;
import com.loopers.domain.ranking.batch.RankingStagingRankRow;
import com.loopers.domain.ranking.batch.RankingStagingRepository;
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
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "outbox.relay.enabled=false"
})
@TestPropertySource(properties = "spring.batch.job.name=" + RankingBatchJobConfig.JOB_NAME)
@Import({
        MySqlTestContainersConfig.class,
        RedisTestContainersConfig.class,
        RankingPublishOnlyJobTestConfig.class
})
class RankingPublishFailurePreservesMvIntegrationTest {

    private static final List<String> TRUNCATE_TABLES = List.of(
            "mv_product_rank_staging",
            "mv_product_rank_weekly",
            "mv_product_rank_monthly",
            "product_metrics",
            "outbox_event"
    );

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier(RankingPublishOnlyJobTestConfig.BEAN_NAME)
    private Job publishOnlyJob;

    @Autowired
    private RankingStagingRepository rankingStagingRepository;

    @Autowired
    private MvProductRankWeeklyJpaRepository weeklyJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

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
    @DisplayName("publish 검증 실패 시 기존 주간 MV 행이 유지된다(half-written 비노출).")
    void launchPublishOnly_whenStagingInvalid_shouldFailWithoutReplacingMv() throws Exception {
        String periodKey = "2026W15";
        Instant at = Instant.parse("2026-04-10T00:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO mv_product_rank_weekly
                (period_key, product_id, `rank`, score, version, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                periodKey,
                99L,
                1,
                new BigDecimal("0.50"),
                1,
                Timestamp.from(at)
        );

        rankingStagingRepository.deleteByPeriodTypeAndPeriodKey("WEEKLY", periodKey);
        rankingStagingRepository.saveRankedRows(
                "WEEKLY",
                periodKey,
                List.of(
                        new RankingStagingRankRow(1, 1L, BigDecimal.ONE),
                        new RankingStagingRankRow(3, 2L, BigDecimal.TEN)
                )
        );

        JobExecution execution = jobLauncher.run(
                publishOnlyJob,
                new JobParametersBuilder()
                        .addString(RankingBatchJobParameters.JOB_PARAM_PERIOD, "WEEKLY")
                        .addString(RankingBatchJobParameters.JOB_PARAM_PERIOD_KEY, periodKey)
                        .addLong("run.id", System.currentTimeMillis())
                        .toJobParameters()
        );

        var rows = weeklyJpaRepository.findByPeriodKeyOrderByRankValueAsc(periodKey);

        assertAll(
                () -> assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode()),
                () -> assertThat(rows).hasSize(1),
                () -> assertThat(rows.get(0).getProductId()).isEqualTo(99L),
                () -> assertThat(rows.get(0).getRankValue()).isEqualTo(1),
                () -> assertThat(rows.get(0).getScore()).isEqualByComparingTo(new BigDecimal("0.50"))
        );
    }
}
