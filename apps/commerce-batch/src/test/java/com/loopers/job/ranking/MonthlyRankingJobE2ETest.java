package com.loopers.job.ranking;

import com.loopers.batch.infrastructure.ranking.repository.MonthlyRankingJpaRepository;
import com.loopers.batch.job.ranking.MonthlyRankingJobConfig;
import com.loopers.testcontainers.MySqlTestContainersConfig;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@Import(MySqlTestContainersConfig.class)
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
    private MonthlyRankingJpaRepository monthlyRankingJpaRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM mv_product_rank_monthly");
        jdbcTemplate.execute("DELETE FROM product_metrics_daily");
    }

    @DisplayName("대상 month의 top100을 적재한다")
    @Test
    void success() throws Exception {
        seedMonth(LocalDate.of(2026, 4, 15));

        jobLauncherTestUtils.setJob(job);
        var jobExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("targetDate", "20260415")
                .toJobParameters());

        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

        var rows = monthlyRankingJpaRepository.findByPeriodKeyOrderByRankNoAsc("2026-04");
        assertThat(rows).hasSize(100);
        assertThat(rows.get(0).getRankNo()).isEqualTo(1);
        assertThat(rows.get(99).getRankNo()).isEqualTo(100);
        assertThat(rows.get(0).getScore()).isGreaterThanOrEqualTo(rows.get(1).getScore());
    }

    private void seedMonth(LocalDate baseDate) {
        List<Integer> productIds = IntStream.rangeClosed(1, 150).boxed().toList();
        for (LocalDate date = baseDate.withDayOfMonth(1); !date.isAfter(baseDate.withDayOfMonth(baseDate.lengthOfMonth())); date = date.plusDays(1)) {
            for (Integer productId : productIds) {
                long viewCount = 200 - productId;
                long likeCount = 150 - productId;
                long orderCount = 100 - productId;
                double score = (viewCount * 0.1d) + (likeCount * 0.2d) + (orderCount * 0.7d);
                jdbcTemplate.update("""
                        INSERT INTO product_metrics_daily
                            (metric_date, product_id, view_count, like_count, order_count, score, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        date, productId.longValue(), viewCount, likeCount, orderCount, score,
                        LocalDateTime.now(), LocalDateTime.now());
            }
        }
    }
}
