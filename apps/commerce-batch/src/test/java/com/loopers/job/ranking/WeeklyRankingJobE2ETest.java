package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;
import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
    "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
class WeeklyRankingJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(WeeklyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MvProductRankWeeklyJpaRepository mvRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static boolean tableCreated = false;

    @BeforeAll
    static void createTable(@Autowired JdbcTemplate jdbcTemplate) {
        if (!tableCreated) {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS product_metrics_daily (
                    product_id BIGINT NOT NULL,
                    metric_date DATE NOT NULL,
                    view_count BIGINT NOT NULL DEFAULT 0,
                    like_count BIGINT NOT NULL DEFAULT 0,
                    order_count BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (product_id, metric_date)
                ) ENGINE=InnoDB
                """);
            tableCreated = true;
        }
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM product_metrics_daily");
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주간 랭킹 배치 — 7일치 daily 데이터를 집계하여 TOP 순위를 생성한다")
    @Test
    void weeklyRankingJob_aggregatesDailyMetrics() throws Exception {
        // arrange
        LocalDate weekStart = LocalDate.of(2026, 4, 13); // 월요일
        insertDailyMetrics(1L, weekStart, 100, 50, 10);
        insertDailyMetrics(1L, weekStart.plusDays(1), 200, 30, 5);
        insertDailyMetrics(2L, weekStart, 50, 10, 20);
        insertDailyMetrics(3L, weekStart.plusDays(2), 10, 5, 1);

        jobLauncherTestUtils.setJob(job);

        var jobParameters = new JobParametersBuilder()
            .addString("weekStartDate", weekStart.format(DateTimeFormatter.BASIC_ISO_DATE))
            .addLong("run.id", System.nanoTime())
            .toJobParameters();

        // act
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // assert
        List<MvProductRankWeekly> results = mvRepository.findAll();

        assertAll(
            () -> assertThat(jobExecution.getExitStatus().getExitCode())
                .isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(results).hasSize(3),
            () -> assertThat(results.get(0).getRankPosition()).isEqualTo(1),
            () -> assertThat(results.get(0).getWeekStartDate()).isEqualTo(weekStart)
        );

        // 상품1: view(300)*1 + like(80)*2 + order(15)*7 = 300+160+105 = 565
        // 상품2: view(50)*1 + like(10)*2 + order(20)*7 = 50+20+140 = 210
        // 상품3: view(10)*1 + like(5)*2 + order(1)*7 = 10+10+7 = 27
        MvProductRankWeekly rank1 = results.stream()
            .filter(r -> r.getRankPosition() == 1).findFirst().orElseThrow();
        MvProductRankWeekly rank2 = results.stream()
            .filter(r -> r.getRankPosition() == 2).findFirst().orElseThrow();

        assertAll(
            () -> assertThat(rank1.getProductId()).isEqualTo(1L),
            () -> assertThat(rank1.getTotalScore()).isEqualTo(565.0),
            () -> assertThat(rank2.getProductId()).isEqualTo(2L),
            () -> assertThat(rank2.getTotalScore()).isEqualTo(210.0)
        );
    }

    @DisplayName("주간 랭킹 배치 — 재실행 시 기존 데이터를 삭제 후 재적재한다 (멱등)")
    @Test
    void weeklyRankingJob_isIdempotent() throws Exception {
        // arrange
        LocalDate weekStart = LocalDate.of(2026, 4, 13);
        insertDailyMetrics(1L, weekStart, 100, 50, 10);

        jobLauncherTestUtils.setJob(job);

        var firstParams = new JobParametersBuilder()
            .addString("weekStartDate", weekStart.format(DateTimeFormatter.BASIC_ISO_DATE))
            .addLong("run.id", System.nanoTime())
            .toJobParameters();

        // act — 2번 실행
        jobLauncherTestUtils.launchJob(firstParams);

        var secondParams = new JobParametersBuilder()
            .addString("weekStartDate", weekStart.format(DateTimeFormatter.BASIC_ISO_DATE))
            .addLong("run.id", System.nanoTime() + 1)
            .toJobParameters();
        var secondExecution = jobLauncherTestUtils.launchJob(secondParams);

        // assert — 중복 없이 1건만 존재
        assertAll(
            () -> assertThat(secondExecution.getExitStatus().getExitCode())
                .isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(mvRepository.findAll()).hasSize(1)
        );
    }

    private void insertDailyMetrics(Long productId, LocalDate metricDate,
                                    long viewCount, long likeCount, long orderCount) {
        jdbcTemplate.update(
            """
                INSERT INTO product_metrics_daily (product_id, metric_date, view_count, like_count, order_count)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    view_count = view_count + VALUES(view_count),
                    like_count = like_count + VALUES(like_count),
                    order_count = order_count + VALUES(order_count)
                """,
            productId, metricDate, viewCount, likeCount, orderCount
        );
    }
}
