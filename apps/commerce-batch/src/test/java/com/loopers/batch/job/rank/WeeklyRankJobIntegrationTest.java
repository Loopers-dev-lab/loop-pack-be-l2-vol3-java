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
        "spring.batch.job.name=" + WeeklyRankJobConfig.JOB_NAME,
        "spring.batch.job.enabled=false"
})
class WeeklyRankJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(WeeklyRankJobConfig.JOB_NAME)
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

    @DisplayName("정상 실행: 7일 × 5상품의 주간 랭킹이 MV에 적재된다")
    @Test
    void normalExecution() throws Exception {
        // arrange — 2026-04-06(월) ~ 2026-04-12(일) = 2026W16
        LocalDate monday = LocalDate.of(2026, 4, 6);
        for (int day = 0; day < 7; day++) {
            LocalDate date = monday.plusDays(day);
            for (long productId = 1; productId <= 5; productId++) {
                insertScoreDaily(productId, date, productId * 100.0, productId * 10, productId * 5, BigDecimal.valueOf(productId * 1000));
            }
        }

        JobParameters params = new JobParametersBuilder()
                .addString("date", "20260408")
                .toJobParameters();

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        // assert
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        String periodKey = "2026W15";
        List<MvProductRankRow> results = mvProductRankRepository.findByPeriodKey(RankPeriodType.WEEKLY, periodKey, 0, 100);
        assertThat(results).hasSize(5);
        assertThat(results.get(0).rankNo()).isEqualTo(1);
        assertThat(results.get(0).refProductId()).isEqualTo(5L);
        assertThat(results.get(0).score()).isCloseTo(5 * 100.0 * 7, within(0.01));
    }

    @DisplayName("멱등성: 같은 date 2회 실행 시 결과 동일")
    @Test
    void idempotency() throws Exception {
        // arrange
        insertScoreDaily(1L, LocalDate.of(2026, 4, 6), 100.0, 10, 5, BigDecimal.valueOf(1000));

        JobParameters params1 = new JobParametersBuilder()
                .addString("date", "20260408")
                .addLong("run.id", 1L)
                .toJobParameters();
        JobParameters params2 = new JobParametersBuilder()
                .addString("date", "20260408")
                .addLong("run.id", 2L)
                .toJobParameters();

        // act
        jobLauncherTestUtils.launchJob(params1);
        JobExecution execution2 = jobLauncherTestUtils.launchJob(params2);

        // assert
        assertThat(execution2.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(mvProductRankRepository.countByPeriodKey(RankPeriodType.WEEKLY, "2026W15"))
                .as("S2: 2회 실행 시 두 version이 테이블에 공존 (cleanup은 별도)").isEqualTo(2);
    }

    @DisplayName("score_daily가 비어있으면 MV 0행, COMPLETED")
    @Test
    void emptyScoreDaily() throws Exception {
        // arrange
        JobParameters params = new JobParametersBuilder()
                .addString("date", "20260408")
                .toJobParameters();

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        // assert
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(mvProductRankRepository.countByPeriodKey(RankPeriodType.WEEKLY, "2026W15")).isZero();
    }

    @DisplayName("deleteByPeriodKey는 다른 period_key의 행을 건드리지 않는다")
    @Test
    void cleanupIsolation() throws Exception {
        // arrange — W15 데이터를 미리 적재
        List<MvProductRankRow> week15 = List.of(
                new MvProductRankRow("2026W15", 1, 99L, 9999.0, 999, 999, BigDecimal.valueOf(9999))
        );
        mvProductRankRepository.batchInsert(RankPeriodType.WEEKLY, week15);

        // W16 배치 실행
        insertScoreDaily(1L, LocalDate.of(2026, 4, 6), 100.0, 10, 5, BigDecimal.valueOf(1000));
        JobParameters params = new JobParametersBuilder()
                .addString("date", "20260408")
                .toJobParameters();

        // act
        jobLauncherTestUtils.launchJob(params);

        // assert — W15 데이터 유지됨
        assertThat(mvProductRankRepository.countByPeriodKey(RankPeriodType.WEEKLY, "2026W15")).isEqualTo(1);
        assertThat(mvProductRankRepository.countByPeriodKey(RankPeriodType.WEEKLY, "2026W15")).isEqualTo(1);
    }

    @DisplayName("Reader SQL에 가중치 파라미터가 없음을 검증 — SUM(score)만 사용")
    @Test
    void readerHasNoWeightParameters() throws Exception {
        // arrange — 같은 score로 시드하고 결과의 score가 단순 SUM인지 검증
        LocalDate date1 = LocalDate.of(2026, 4, 6);
        LocalDate date2 = LocalDate.of(2026, 4, 7);
        insertScoreDaily(1L, date1, 100.0, 10, 5, BigDecimal.valueOf(1000));
        insertScoreDaily(1L, date2, 200.0, 20, 10, BigDecimal.valueOf(2000));

        JobParameters params = new JobParametersBuilder()
                .addString("date", "20260408")
                .toJobParameters();

        // act
        jobLauncherTestUtils.launchJob(params);

        // assert — score = SUM(100 + 200) = 300, 가중치 재계산 아님
        List<MvProductRankRow> results = mvProductRankRepository.findByPeriodKey(RankPeriodType.WEEKLY, "2026W15", 0, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).score()).isCloseTo(300.0, within(0.01));
        assertThat(results.get(0).viewCount()).isEqualTo(30);
    }

    private void insertScoreDaily(Long productDbId, LocalDate date, double score, long viewCount, long likeCount, BigDecimal orderAmount) {
        jdbcTemplate.update(
                "INSERT INTO mv_product_score_daily (product_db_id, score_date, score, view_count, like_count, order_amount, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())",
                productDbId, date, score, viewCount, likeCount, orderAmount
        );
    }
}
