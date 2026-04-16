package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.weekly.WeeklyRankingJobConfig;
import com.loopers.domain.ranking.ProductRankWeeklyModel;
import com.loopers.infrastructure.ranking.ProductRankWeeklyJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME)
class WeeklyRankingJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(WeeklyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private ProductRankWeeklyJpaRepository productRankWeeklyJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("weeklyRankingJob - product_metrics 데이터를 기반으로 주간 랭킹 Top 100을 생성한다")
    @Test
    void createsWeeklyRankingTop100() throws Exception {
        // arrange
        insertProductMetrics(1L, 1000L, 50L, 10L);  // score = 1000*0.1 + 50*0.2 + 10*0.6 = 116
        insertProductMetrics(2L, 500L, 100L, 20L);   // score = 500*0.1 + 100*0.2 + 20*0.6 = 82
        insertProductMetrics(3L, 2000L, 200L, 30L);  // score = 2000*0.1 + 200*0.2 + 30*0.6 = 258

        jobLauncherTestUtils.setJob(job);

        // act
        var jobExecution = jobLauncherTestUtils.launchJob(
            new JobParametersBuilder()
                .addLocalDate("requestDate", LocalDate.of(2026, 4, 15))
                .toJobParameters()
        );

        // assert
        List<ProductRankWeeklyModel> rankings = productRankWeeklyJpaRepository.findAll();

        assertAll(
            () -> assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(rankings).hasSize(3),
            () -> assertThat(rankings.get(0).getProductId()).isEqualTo(3L),
            () -> assertThat(rankings.get(0).getRankNumber()).isEqualTo(1),
            () -> assertThat(rankings.get(0).getScore()).isEqualTo(258.0),
            () -> assertThat(rankings.get(0).getYearWeek()).isEqualTo("2026-W16"),
            () -> assertThat(rankings.get(1).getProductId()).isEqualTo(1L),
            () -> assertThat(rankings.get(1).getRankNumber()).isEqualTo(2),
            () -> assertThat(rankings.get(2).getProductId()).isEqualTo(2L),
            () -> assertThat(rankings.get(2).getRankNumber()).isEqualTo(3)
        );
    }

    @DisplayName("weeklyRankingJob - 동일 yearWeek 재실행 시 기존 데이터를 삭제 후 재생성한다 (멱등성)")
    @Test
    void idempotentRerun() throws Exception {
        // arrange
        insertProductMetrics(1L, 100L, 10L, 5L);

        jobLauncherTestUtils.setJob(job);
        var params = new JobParametersBuilder()
            .addLocalDate("requestDate", LocalDate.of(2026, 4, 15))
            .toJobParameters();

        // act - 첫 번째 실행
        jobLauncherTestUtils.launchJob(params);
        // act - 두 번째 실행 (RunIdIncrementer로 다른 run.id)
        var jobExecution = jobLauncherTestUtils.launchJob(params);

        // assert
        List<ProductRankWeeklyModel> rankings = productRankWeeklyJpaRepository.findAll();

        assertAll(
            () -> assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode()),
            () -> assertThat(rankings).hasSize(1),
            () -> assertThat(rankings.get(0).getRankNumber()).isEqualTo(1)
        );
    }

    private void insertProductMetrics(Long productId, long viewCount, long likeCount, long salesQuantity) {
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, view_count, like_count, sales_quantity, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, NOW(), NOW())",
            productId, viewCount, likeCount, salesQuantity
        );
    }
}
