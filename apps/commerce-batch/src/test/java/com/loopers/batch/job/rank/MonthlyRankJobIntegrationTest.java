package com.loopers.batch.job.rank;

import com.loopers.domain.rank.MvProductRankRepository;
import com.loopers.domain.rank.MvProductRankRow;
import com.loopers.domain.rank.RankPeriodType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        "spring.batch.job.name=" + MonthlyRankJobConfig.JOB_NAME,
        "spring.batch.job.enabled=false"
})
class MonthlyRankJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(MonthlyRankJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MvProductRankRepository mvProductRankRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
        databaseCleanUp.truncateAllTables();
        cleanBatchMetaTables();
    }

    private void cleanBatchMetaTables() {
        jdbcTemplate.execute("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT");
        jdbcTemplate.execute("DELETE FROM BATCH_STEP_EXECUTION");
        jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT");
        jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION_PARAMS");
        jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION");
        jdbcTemplate.execute("DELETE FROM BATCH_JOB_INSTANCE");
    }

    @DisplayName("정상 실행: 30일 × 3상품의 월간 랭킹이 MV에 적재된다")
    @Test
    void normalExecution() throws Exception {
        // arrange — 2026년 4월 (1일~30일)
        for (int day = 1; day <= 30; day++) {
            LocalDate date = LocalDate.of(2026, 4, day);
            for (long productId = 1; productId <= 3; productId++) {
                insertScoreDaily(productId, date, productId * 10.0, productId, productId, BigDecimal.valueOf(productId * 100));
            }
        }

        JobParameters params = new JobParametersBuilder()
                .addString("date", "20260415")
                .toJobParameters();

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        // assert
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        String periodKey = "202604";
        List<MvProductRankRow> results = mvProductRankRepository.findByPeriodKey(RankPeriodType.MONTHLY, periodKey, 0, 100);
        assertThat(results).hasSize(3);
        assertThat(results.get(0).rankNo()).isEqualTo(1);
        assertThat(results.get(0).refProductId()).isEqualTo(3L);
        assertThat(results.get(0).score()).isCloseTo(3 * 10.0 * 30, within(0.01));
    }

    @DisplayName("월 경계: 3월 데이터와 4월 데이터가 분리된다")
    @Test
    void monthBoundary() throws Exception {
        // arrange
        insertScoreDaily(1L, LocalDate.of(2026, 3, 31), 999.0, 99, 99, BigDecimal.valueOf(9999));
        insertScoreDaily(1L, LocalDate.of(2026, 4, 1), 100.0, 10, 5, BigDecimal.valueOf(1000));

        JobParameters params = new JobParametersBuilder()
                .addString("date", "20260415")
                .toJobParameters();

        // act
        jobLauncherTestUtils.launchJob(params);

        // assert — 4월 데이터만 집계
        List<MvProductRankRow> results = mvProductRankRepository.findByPeriodKey(RankPeriodType.MONTHLY, "202604", 0, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).score()).isCloseTo(100.0, within(0.01));
    }

    @DisplayName("윤년 2월: monthEnd가 29일이다")
    @Test
    void leapYearFebruary() throws Exception {
        // arrange — 2028년 2월 (윤년)
        insertScoreDaily(1L, LocalDate.of(2028, 2, 28), 100.0, 10, 5, BigDecimal.valueOf(1000));
        insertScoreDaily(1L, LocalDate.of(2028, 2, 29), 200.0, 20, 10, BigDecimal.valueOf(2000));

        JobParameters params = new JobParametersBuilder()
                .addString("date", "20280215")
                .toJobParameters();

        // act
        jobLauncherTestUtils.launchJob(params);

        // assert — 28일 + 29일 모두 포함
        List<MvProductRankRow> results = mvProductRankRepository.findByPeriodKey(RankPeriodType.MONTHLY, "202802", 0, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).score()).isCloseTo(300.0, within(0.01));
    }

    private void insertScoreDaily(Long productDbId, LocalDate date, double score, long viewCount, long likeCount, BigDecimal orderAmount) {
        jdbcTemplate.update(
                "INSERT INTO mv_product_score_daily (product_db_id, score_date, score, view_count, like_count, order_amount, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())",
                productDbId, date, score, viewCount, likeCount, orderAmount
        );
    }
}
