package com.loopers.batch.job.score;

import com.loopers.domain.score.MvProductScoreDailyId;
import com.loopers.domain.score.MvProductScoreDailyModel;
import com.loopers.infrastructure.score.MvProductScoreDailyJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        "spring.batch.job.name=" + DailyScoreJobConfig.JOB_NAME,
        "spring.batch.job.enabled=false"
})
class DailyScoreJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(DailyScoreJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MvProductScoreDailyJpaRepository scoreDailyJpaRepository;

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

    @DisplayName("정상 실행: 3개 상품의 일간 score가 mv_product_score_daily에 적재된다")
    @Test
    void normalExecution() throws Exception {
        // arrange
        LocalDate date = LocalDate.of(2026, 4, 11);
        insertSignal(1L, date, 100, 50, BigDecimal.valueOf(1000));
        insertSignal(2L, date, 50, 25, BigDecimal.valueOf(500));
        insertSignal(3L, date, 200, 100, BigDecimal.valueOf(2000));

        JobParameters params = new JobParametersBuilder()
                .addString("date", "20260411")
                .toJobParameters();

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        // assert
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<MvProductScoreDailyModel> results = scoreDailyJpaRepository.findAll();
        assertThat(results).hasSize(3);

        MvProductScoreDailyModel first = scoreDailyJpaRepository
                .findById(new MvProductScoreDailyId(1L, date)).orElseThrow();
        assertThat(first.getScore()).isCloseTo(720.0, within(0.001));

        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getReadCount()).isEqualTo(3);
        assertThat(stepExecution.getWriteCount()).isEqualTo(3);
    }

    @DisplayName("멱등성: 동일 date로 2회 실행 시 결과가 동일하다 (UPSERT)")
    @Test
    void idempotency() throws Exception {
        // arrange
        LocalDate date = LocalDate.of(2026, 4, 11);
        insertSignal(1L, date, 100, 50, BigDecimal.valueOf(1000));

        JobParameters params1 = new JobParametersBuilder()
                .addString("date", "20260411")
                .addLong("run.id", 1L)
                .toJobParameters();
        JobParameters params2 = new JobParametersBuilder()
                .addString("date", "20260411")
                .addLong("run.id", 2L)
                .toJobParameters();

        // act
        jobLauncherTestUtils.launchJob(params1);
        JobExecution execution2 = jobLauncherTestUtils.launchJob(params2);

        // assert
        assertThat(execution2.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(scoreDailyJpaRepository.findAll()).hasSize(1);
    }

    @DisplayName("score 0인 상품은 필터링된다")
    @Test
    void zeroScoreFiltered() throws Exception {
        // arrange
        LocalDate date = LocalDate.of(2026, 4, 11);
        insertSignal(1L, date, 100, 50, BigDecimal.valueOf(1000));
        insertSignal(2L, date, 0, 0, BigDecimal.ZERO);

        JobParameters params = new JobParametersBuilder()
                .addString("date", "20260411")
                .toJobParameters();

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        // assert
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(scoreDailyJpaRepository.findAll()).hasSize(1);

        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getFilterCount()).isEqualTo(1);
    }

    @DisplayName("해당 날짜에 signal이 없으면 0행 적재, COMPLETED")
    @Test
    void noSignals() throws Exception {
        // arrange
        JobParameters params = new JobParametersBuilder()
                .addString("date", "20260411")
                .toJobParameters();

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        // assert
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(scoreDailyJpaRepository.findAll()).isEmpty();
    }

    private void insertSignal(Long productDbId, LocalDate date, long viewCount, long likeCount, BigDecimal orderAmount) {
        jdbcTemplate.update(
                "INSERT INTO product_daily_signals (product_db_id, signal_date, view_count, like_count, order_amount, created_at, updated_at) VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
                productDbId, date, viewCount, likeCount, orderAmount
        );
    }
}
