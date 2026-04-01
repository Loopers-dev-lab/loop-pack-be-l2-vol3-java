package com.loopers.job.payment;

import com.loopers.batch.job.paymentrecovery.PaymentRecoveryJobConfig;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B7-1: 결제 복구 배치 E2E 테스트.
 *
 * <p>REQUESTED(1분+), PENDING(5분+), UNKNOWN(10분+) 결제건을 FAILED로 전이.</p>
 *
 * <p>실행 조건: Docker (MySQL + Redis Testcontainers) 필요.</p>
 *
 * @see <a href="05-payment-resilience.md §10.2">결제 복구 배치</a>
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + PaymentRecoveryJobConfig.JOB_NAME)
@Sql(scripts = "/schema-batch-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class PaymentRecoveryJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(PaymentRecoveryJobConfig.JOB_NAME)
    private Job job;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
    }

    @DisplayName("B7-1: 결제 복구 배치 → 정상 실행")
    @Test
    void paymentRecoveryJob_success() throws Exception {
        // TODO: DB에 REQUESTED(1분+), PENDING(5분+), UNKNOWN(10분+) 결제 데이터 삽입

        var jobParameters = new JobParametersBuilder()
            .addLocalDate("requestDate", LocalDate.now())
            .toJobParameters();
        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        assertThat(jobExecution).isNotNull();
        assertThat(jobExecution.getExitStatus().getExitCode())
            .isEqualTo(ExitStatus.COMPLETED.getExitCode());
    }
}
