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
        "spring.batch.job.name=" + QuarterlyRankJobConfig.JOB_NAME,
        "spring.batch.job.enabled=false"
})
class QuarterlyRankJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(QuarterlyRankJobConfig.JOB_NAME)
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

    @DisplayName("Approach A 정상 실행: 90일 × 3상품의 분기 랭킹이 MV에 적재된다")
    @Test
    void normalExecution() throws Exception {
        LocalDate endDate = LocalDate.of(2026, 4, 16);
        LocalDate startDate = endDate.minusDays(89);

        for (int day = 0; day < 90; day++) {
            LocalDate date = startDate.plusDays(day);
            for (long productId = 1; productId <= 3; productId++) {
                insertScoreDaily(productId, date, productId * 10.0, productId, productId, BigDecimal.valueOf(productId * 100));
            }
        }

        JobParameters params = new JobParametersBuilder()
                .addString("date", "20260416")
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        String periodKey = "20260416";
        List<MvProductRankRow> results = mvProductRankRepository.findByPeriodKey(RankPeriodType.QUARTERLY, periodKey, 0, 100);
        assertThat(results).hasSize(3);
        assertThat(results.get(0).rankNo()).isEqualTo(1);
        assertThat(results.get(0).refProductId()).isEqualTo(3L);
        assertThat(results.get(0).score()).isCloseTo(3 * 10.0 * 90, within(0.01));
    }

    @DisplayName("Approach A 롤링 윈도우: 91일 전 데이터는 제외된다")
    @Test
    void rollingWindowExclusion() throws Exception {
        LocalDate endDate = LocalDate.of(2026, 4, 16);

        insertScoreDaily(1L, endDate.minusDays(90), 999.0, 99, 99, BigDecimal.valueOf(9999));
        insertScoreDaily(1L, endDate.minusDays(89), 100.0, 10, 5, BigDecimal.valueOf(1000));
        insertScoreDaily(2L, endDate, 50.0, 5, 2, BigDecimal.valueOf(500));

        JobParameters params = new JobParametersBuilder()
                .addString("date", "20260416")
                .toJobParameters();

        jobLauncherTestUtils.launchJob(params);

        List<MvProductRankRow> results = mvProductRankRepository.findByPeriodKey(RankPeriodType.QUARTERLY, "20260416", 0, 10);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).refProductId()).isEqualTo(1L);
        assertThat(results.get(0).score()).isCloseTo(100.0, within(0.01));
    }

    @DisplayName("Approach A 멱등성: 2회 실행 후 version만 증가하고 product·score는 동일")
    @Test
    void idempotency() throws Exception {
        LocalDate endDate = LocalDate.of(2026, 4, 16);
        for (int day = 0; day < 7; day++) {
            insertScoreDaily(1L, endDate.minusDays(day), 100.0, 10, 5, BigDecimal.valueOf(1000));
        }
        double expectedScore = 100.0 * 7;

        JobParameters params1 = new JobParametersBuilder()
                .addString("date", "20260416")
                .toJobParameters();
        jobLauncherTestUtils.launchJob(params1);

        List<MvProductRankRow> afterRun1 = mvProductRankRepository.findByPeriodKey(RankPeriodType.QUARTERLY, "20260416", 0, 10);
        assertThat(afterRun1).hasSize(1);
        assertThat(afterRun1.get(0).refProductId()).isEqualTo(1L);
        assertThat(afterRun1.get(0).score()).isCloseTo(expectedScore, within(0.01));
        long versionAfterRun1 = afterRun1.get(0).version();

        cleanBatchMetaTables();

        JobParameters params2 = new JobParametersBuilder()
                .addString("date", "20260416")
                .toJobParameters();
        jobLauncherTestUtils.launchJob(params2);

        List<MvProductRankRow> afterRun2 = mvProductRankRepository.findByPeriodKey(RankPeriodType.QUARTERLY, "20260416", 0, 10);
        assertThat(afterRun2).hasSize(2);
        assertThat(afterRun2).extracting(MvProductRankRow::refProductId).containsOnly(1L);
        assertThat(afterRun2).extracting(MvProductRankRow::score)
                .allSatisfy(score -> assertThat(score).isCloseTo(expectedScore, within(0.01)));
        assertThat(afterRun2).extracting(MvProductRankRow::version)
                .anyMatch(v -> v > versionAfterRun1);
    }

    @DisplayName("Approach A 빈 데이터: 스코어 없으면 COMPLETED, MV 0행")
    @Test
    void emptyData() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("date", "20260416")
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        long count = mvProductRankRepository.countByPeriodKey(RankPeriodType.QUARTERLY, "20260416");
        assertThat(count).isZero();
    }

    private void insertScoreDaily(Long productDbId, LocalDate date, double score, long viewCount, long likeCount, BigDecimal orderAmount) {
        jdbcTemplate.update(
                "INSERT INTO mv_product_score_daily (product_db_id, score_date, score, view_count, like_count, order_amount, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())",
                productDbId, date, score, viewCount, likeCount, orderAmount
        );
    }
}
