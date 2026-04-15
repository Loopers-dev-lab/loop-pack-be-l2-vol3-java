package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.monthly.MonthlyRankingJobConfig;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + MonthlyRankingJobConfig.JOB_NAME)
class MonthlyRankingJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(MonthlyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
        jdbcTemplate.execute("DELETE FROM mv_product_rank_monthly");
        jdbcTemplate.execute("DELETE FROM product_metrics");
    }

    @DisplayName("targetYearMonth 파라미터 없으면 Job 이 실패한다.")
    @Test
    void failsWithoutTargetDate() throws Exception {
        // arrange & act
        var execution = jobLauncherTestUtils.launchJob();

        // assert
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
    }

    @DisplayName("집계 대상 메트릭이 없으면 MV 테이블은 비어 있다.")
    @Test
    void emptyMetrics_noRankingStored() throws Exception {
        // arrange
        var params = new JobParametersBuilder()
                .addString("targetYearMonth", "202604")
                .toJobParameters();

        // act
        var execution = jobLauncherTestUtils.launchJob(params);

        // assert
        assertAll(
                () -> assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
                () -> assertThat(countMv()).isZero()
        );
    }

    @DisplayName("상품이 100개 이하이면 모두 랭킹에 기록되고, rank 1 이 가장 높은 score 를 가진다.")
    @Test
    void fewProducts_allRankedInOrder() throws Exception {
        // arrange
        insertMetrics(1L, LocalDateTime.of(2026, 4, 16, 0, 0), 1, 1, 1, 1_000);
        insertMetrics(2L, LocalDateTime.of(2026, 4, 16, 0, 0), 5, 5, 5, 50_000);

        var params = new JobParametersBuilder()
                .addString("targetYearMonth", "202604")
                .toJobParameters();

        // act
        var execution = jobLauncherTestUtils.launchJob(params);

        // assert
        Long rank1ProductId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM mv_product_rank_monthly WHERE rank = 1", Long.class);
        assertAll(
                () -> assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
                () -> assertThat(countMv()).isEqualTo(2),
                () -> assertThat(rank1ProductId).isEqualTo(2L)
        );
    }

    @DisplayName("상품이 100개 초과이면 상위 100개만 기록된다.")
    @Test
    void manyProducts_top100Only() throws Exception {
        // arrange
        for (long i = 1; i <= 110; i++) {
            insertMetrics(i, LocalDateTime.of(2026, 4, 16, 0, 0), 1, 1, 1, i * 1_000);
        }

        var params = new JobParametersBuilder()
                .addString("targetYearMonth", "202604")
                .toJobParameters();

        // act
        var execution = jobLauncherTestUtils.launchJob(params);

        // assert
        assertAll(
                () -> assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
                () -> assertThat(countMv()).isEqualTo(100)
        );
    }

    private void insertMetrics(long productId, LocalDateTime metricHour,
                                long likeCount, long orderCount, long viewCount, long salesAmount) {
        jdbcTemplate.update(
                "INSERT INTO product_metrics (product_id, metric_hour, like_count, order_count, view_count, sales_amount, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())",
                productId, metricHour, likeCount, orderCount, viewCount, salesAmount
        );
    }

    private long countMv() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mv_product_rank_monthly", Long.class);
        return count != null ? count : 0L;
    }
}
