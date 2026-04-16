package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.RankingScoreCalculator;
import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Sql(scripts = "classpath:schema/ranking-tables.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class WeeklyRankingJobE2ETest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(WeeklyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.update("DELETE FROM product_metrics");
    }

    @DisplayName("주간 랭킹 배치를 실행하면, 해당 주의 product_metrics를 집계해 mv_product_rank_weekly에 적재한다.")
    @Test
    void weeklyRankingJob_aggregatesAndWritesToMvTable() throws Exception {
        // arrange
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // product 1: 2일치 — like 총 7, order 총 4 → score = 7*0.2 + 4*0.7 = 4.2
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, date, like_count, order_count) VALUES (?, ?, ?, ?)",
            1L, monday, 5, 3
        );
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, date, like_count, order_count) VALUES (?, ?, ?, ?)",
            1L, monday.plusDays(1), 2, 1
        );
        // product 2: 1일치 — like 10, order 0 → score = 10*0.2 = 2.0
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, date, like_count, order_count) VALUES (?, ?, ?, ?)",
            2L, monday, 10, 0
        );

        jobLauncherTestUtils.setJob(job);

        // act
        var params = new JobParametersBuilder()
            .addString("targetDate", monday.format(DATE_FORMATTER))
            .addLong("runId", System.nanoTime())
            .toJobParameters();
        var execution = jobLauncherTestUtils.launchJob(params);

        // assert
        Integer rowCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly", Integer.class
        );
        Double scoreProduct1 = jdbcTemplate.queryForObject(
            "SELECT score FROM mv_product_rank_weekly WHERE product_id = ?", Double.class, 1L
        );
        Double scoreProduct2 = jdbcTemplate.queryForObject(
            "SELECT score FROM mv_product_rank_weekly WHERE product_id = ?", Double.class, 2L
        );

        assertAll(
            () -> assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(rowCount).isEqualTo(2),
            () -> assertThat(scoreProduct1).isEqualTo(RankingScoreCalculator.calculate(7, 4)),
            () -> assertThat(scoreProduct2).isEqualTo(RankingScoreCalculator.calculate(10, 0))
        );
    }

    @DisplayName("해당 주 범위 밖의 product_metrics는 집계하지 않는다.")
    @Test
    void weeklyRankingJob_excludesMetricsOutsideTargetWeek() throws Exception {
        // arrange
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // 이번 주 데이터
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, date, like_count, order_count) VALUES (?, ?, ?, ?)",
            1L, monday, 3, 2
        );
        // 지난 주 데이터 (범위 밖)
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, date, like_count, order_count) VALUES (?, ?, ?, ?)",
            2L, monday.minusWeeks(1), 10, 10
        );

        jobLauncherTestUtils.setJob(job);

        // act
        var params = new JobParametersBuilder()
            .addString("targetDate", monday.format(DATE_FORMATTER))
            .addLong("runId", System.nanoTime())
            .toJobParameters();
        var execution = jobLauncherTestUtils.launchJob(params);

        // assert: product 2는 지난 주라 집계되지 않음
        Integer rowCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly", Integer.class
        );

        assertAll(
            () -> assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(rowCount).isEqualTo(1)
        );
    }

    @DisplayName("재실행 시 기존 mv_product_rank_weekly 데이터를 삭제하고 새로 적재한다.")
    @Test
    void weeklyRankingJob_truncatesBeforeWrite() throws Exception {
        // arrange: 이전 배치 결과가 남아있다고 가정
        jdbcTemplate.update(
            "INSERT INTO mv_product_rank_weekly (product_id, like_count, order_count, score, year_month_week, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, NOW())",
            999L, 1, 1, 0.9, "2020-W01"
        );
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, date, like_count, order_count) VALUES (?, ?, ?, ?)",
            1L, monday, 5, 3
        );

        jobLauncherTestUtils.setJob(job);

        // act
        var params = new JobParametersBuilder()
            .addString("targetDate", monday.format(DATE_FORMATTER))
            .addLong("runId", System.nanoTime())
            .toJobParameters();
        jobLauncherTestUtils.launchJob(params);

        // assert: product 999 (이전 데이터)는 삭제되고 product 1만 남음
        Integer count999 = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE product_id = ?", Integer.class, 999L
        );
        assertThat(count999).isEqualTo(0);
    }
}
