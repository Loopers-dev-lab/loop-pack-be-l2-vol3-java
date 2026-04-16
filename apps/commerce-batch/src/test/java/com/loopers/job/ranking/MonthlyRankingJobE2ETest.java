package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.MonthlyRankingJobConfig;
import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
    "spring.batch.job.name=" + MonthlyRankingJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
class MonthlyRankingJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(MonthlyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MvProductRankMonthlyJpaRepository mvRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeAll
    static void createTable(@Autowired JdbcTemplate jdbcTemplate) {
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
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM product_metrics_daily");
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("월간 랭킹 배치 — 해당 월 daily 데이터를 집계하여 TOP 순위를 생성한다")
    @Test
    void monthlyRankingJob_aggregatesDailyMetrics() throws Exception {
        // arrange
        LocalDate monthStart = LocalDate.of(2026, 4, 1);
        insertDailyMetrics(1L, monthStart, 100, 50, 10);
        insertDailyMetrics(1L, monthStart.plusDays(15), 200, 30, 5);
        insertDailyMetrics(2L, monthStart.plusDays(10), 50, 10, 20);
        // 다른 달 데이터 — 집계에 포함되면 안 됨
        insertDailyMetrics(3L, LocalDate.of(2026, 3, 31), 999, 999, 999);

        jobLauncherTestUtils.setJob(job);

        var jobParameters = new JobParametersBuilder()
            .addString("monthStartDate", monthStart.format(DateTimeFormatter.BASIC_ISO_DATE))
            .addLong("run.id", System.nanoTime())
            .toJobParameters();

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // assert
        List<MvProductRankMonthly> results = mvRepository.findAll();

        assertAll(
            () -> assertThat(jobExecution.getExitStatus().getExitCode())
                .isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(results).hasSize(2),
            () -> assertThat(results.stream().noneMatch(r -> r.getProductId().equals(3L))).isTrue()
        );

        // 상품1: view(300)*1 + like(80)*2 + order(15)*7 = 565
        // 상품2: view(50)*1 + like(10)*2 + order(20)*7 = 210
        MvProductRankMonthly rank1 = results.stream()
            .filter(r -> r.getRankPosition() == 1).findFirst().orElseThrow();

        assertAll(
            () -> assertThat(rank1.getProductId()).isEqualTo(1L),
            () -> assertThat(rank1.getTotalScore()).isEqualTo(565.0),
            () -> assertThat(rank1.getMonthStartDate()).isEqualTo(monthStart)
        );
    }

    @DisplayName("월간 랭킹 배치 — 재실행 시 기존 데이터를 삭제 후 재적재한다 (멱등)")
    @Test
    void monthlyRankingJob_isIdempotent() throws Exception {
        // arrange
        LocalDate monthStart = LocalDate.of(2026, 4, 1);
        insertDailyMetrics(1L, monthStart, 100, 50, 10);

        jobLauncherTestUtils.setJob(job);

        var firstParams = new JobParametersBuilder()
            .addString("monthStartDate", monthStart.format(DateTimeFormatter.BASIC_ISO_DATE))
            .addLong("run.id", System.nanoTime())
            .toJobParameters();

        // act — 2번 실행
        jobLauncherTestUtils.launchJob(firstParams);

        var secondParams = new JobParametersBuilder()
            .addString("monthStartDate", monthStart.format(DateTimeFormatter.BASIC_ISO_DATE))
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
