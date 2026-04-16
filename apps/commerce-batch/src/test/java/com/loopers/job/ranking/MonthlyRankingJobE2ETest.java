package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.MonthlyRankingJobConfig;
import com.loopers.batch.job.ranking.RankingScoreCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.batch.core.ExitStatus;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + MonthlyRankingJobConfig.JOB_NAME)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Sql(scripts = "classpath:schema/ranking-tables.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class MonthlyRankingJobE2ETest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(MonthlyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly");
        jdbcTemplate.update("DELETE FROM product_metrics");
    }

    @DisplayName("월간 랭킹 배치를 실행하면, 해당 달의 product_metrics를 집계해 mv_product_rank_monthly에 적재한다.")
    @Test
    void monthlyRankingJob_aggregatesAndWritesToMvTable() throws Exception {
        // arrange
        LocalDate firstDay = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        // product 1: 월초 + 15일치 — like 총 8, order 총 5 → score = 8*0.2 + 5*0.7 = 5.1
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, date, like_count, order_count) VALUES (?, ?, ?, ?)",
            1L, firstDay, 5, 3
        );
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, date, like_count, order_count) VALUES (?, ?, ?, ?)",
            1L, firstDay.plusDays(14), 3, 2
        );
        // product 2: 단일 행 — like 0, order 10 → score = 0*0.2 + 10*0.7 = 7.0
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, date, like_count, order_count) VALUES (?, ?, ?, ?)",
            2L, firstDay, 0, 10
        );

        jobLauncherTestUtils.setJob(job);

        // act
        var params = new JobParametersBuilder()
            .addString("targetDate", firstDay.format(DATE_FORMATTER))
            .addLong("runId", System.nanoTime())
            .toJobParameters();
        var execution = jobLauncherTestUtils.launchJob(params);

        // assert
        Integer rowCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_monthly", Integer.class
        );
        Double scoreProduct1 = jdbcTemplate.queryForObject(
            "SELECT score FROM mv_product_rank_monthly WHERE product_id = ?", Double.class, 1L
        );
        Double scoreProduct2 = jdbcTemplate.queryForObject(
            "SELECT score FROM mv_product_rank_monthly WHERE product_id = ?", Double.class, 2L
        );

        assertAll(
            () -> assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(rowCount).isEqualTo(2),
            () -> assertThat(scoreProduct1).isEqualTo(RankingScoreCalculator.calculate(8, 5)),
            () -> assertThat(scoreProduct2).isEqualTo(RankingScoreCalculator.calculate(0, 10))
        );
    }

    @DisplayName("해당 달 범위 밖의 product_metrics는 집계하지 않는다.")
    @Test
    void monthlyRankingJob_excludesMetricsOutsideTargetMonth() throws Exception {
        // arrange
        LocalDate firstDay = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        // 이번 달 데이터
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, date, like_count, order_count) VALUES (?, ?, ?, ?)",
            1L, firstDay, 3, 2
        );
        // 지난 달 데이터 (범위 밖)
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, date, like_count, order_count) VALUES (?, ?, ?, ?)",
            2L, firstDay.minusMonths(1), 10, 10
        );

        jobLauncherTestUtils.setJob(job);

        // act
        var params = new JobParametersBuilder()
            .addString("targetDate", firstDay.format(DATE_FORMATTER))
            .addLong("runId", System.nanoTime())
            .toJobParameters();
        var execution = jobLauncherTestUtils.launchJob(params);

        // assert: product 2는 지난 달이라 집계되지 않음
        Integer rowCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_monthly", Integer.class
        );

        assertAll(
            () -> assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(rowCount).isEqualTo(1)
        );
    }
}
