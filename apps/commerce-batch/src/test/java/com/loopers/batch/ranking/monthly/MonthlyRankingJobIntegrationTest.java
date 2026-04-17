package com.loopers.batch.ranking.monthly;

import com.loopers.domain.rank.MvProductRankMonthly;
import com.loopers.domain.rank.MvProductRankMonthlyRepository;
import com.loopers.fixture.RankingMetricsTestFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=monthlyRankingJob")
class MonthlyRankingJobIntegrationTest {

    private static final LocalDate SNAPSHOT = LocalDate.of(2026, 4, 16);

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier(MonthlyRankingJobConfig.JOB_NAME)
    private Job monthlyRankingJob;

    @Autowired
    private MvProductRankMonthlyRepository repository;

    @Autowired
    private RankingMetricsTestFixture metricsFixture;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(monthlyRankingJob);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    @DisplayName("Rolling Window [today-30, today-1] 범위만 집계에 포함된다")
    void rollingWindowBoundary() throws Exception {
        // arrange: today-31 과 today 데이터 삽입 (제외 대상)
        metricsFixture.insertMetrics(SNAPSHOT.minusDays(31), 1L, 100, 10, 1000);
        metricsFixture.insertMetrics(SNAPSHOT, 1L, 100, 10, 1000);
        // 포함 대상: today-30 ~ today-1
        for (int d = 1; d <= 30; d++) {
            metricsFixture.insertMetrics(SNAPSHOT.minusDays(d), 1L, 10, 1, 100);
        }

        // act
        JobExecution exec = jobLauncherTestUtils.launchJob(jobParams(SNAPSHOT));

        // assert
        assertThat(exec.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        MvProductRankMonthly row = repository.findBySnapshotDateAndProductId(SNAPSHOT, 1L).orElseThrow();
        assertThat(row.getViewCount()).isEqualTo(300L);
        assertThat(row.getLikeCount()).isEqualTo(30L);
        assertThat(row.getOrderRevenue()).isEqualByComparingTo("3000.00");
    }

    @Test
    @DisplayName("TOP 100 만 적재한다 (101위 이하는 제외)")
    void top100Only() throws Exception {
        // arrange: 150 개 상품의 30일치 메트릭 삽입
        for (long pid = 1; pid <= 150; pid++) {
            for (int d = 1; d <= 30; d++) {
                metricsFixture.insertMetrics(SNAPSHOT.minusDays(d), pid, pid, 0, 0);
            }
        }

        // act
        jobLauncherTestUtils.launchJob(jobParams(SNAPSHOT));

        // assert
        assertThat(repository.countBySnapshotDate(SNAPSHOT)).isEqualTo(100);
    }

    @Test
    @DisplayName("같은 snapshotDate 로 재실행하면 JobInstanceAlreadyCompleteException")
    void idempotentReRun() throws Exception {
        // arrange
        metricsFixture.insertMetrics(SNAPSHOT.minusDays(1), 1L, 10, 0, 0);
        jobLauncherTestUtils.launchJob(jobParams(SNAPSHOT));

        // act & assert
        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(jobParams(SNAPSHOT)))
            .isInstanceOf(JobInstanceAlreadyCompleteException.class);
    }

    @Test
    @DisplayName("trigger + run.id 조합으로 재실행 가능하다 (새 JobInstance)")
    void manualReRun() throws Exception {
        // arrange
        metricsFixture.insertMetrics(SNAPSHOT.minusDays(1), 1L, 10, 0, 0);
        jobLauncherTestUtils.launchJob(jobParams(SNAPSHOT));

        JobParameters manual = new JobParametersBuilder()
            .addString("snapshotDate", SNAPSHOT.toString())
            .addString("trigger", "WEIGHT_CHANGE")
            .addString("run.id", LocalDateTime.now().toString())
            .toJobParameters();

        // act
        JobExecution exec = jobLauncherTestUtils.launchJob(manual);

        // assert
        assertThat(exec.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(repository.countBySnapshotDate(SNAPSHOT)).isEqualTo(1);
    }

    private JobParameters jobParams(LocalDate date) {
        return new JobParametersBuilder()
            .addString("snapshotDate", date.toString())
            .toJobParameters();
    }
}
