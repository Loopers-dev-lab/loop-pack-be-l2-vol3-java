package com.loopers.job.payment;

import com.loopers.batch.job.reconciliation.PaymentCouponReconciliationJobConfig;
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
 * B7-3: 쿠폰 대사 배치 E2E 테스트.
 *
 * <p>결제 실패했는데 쿠폰 복원이 누락된 건을 감지하여 자동 복원.</p>
 *
 * <p>실행 조건: Docker (MySQL + Redis Testcontainers) 필요.</p>
 *
 * @see <a href="05-payment-resilience.md §14.3">[R3] 쿠폰 대사</a>
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + PaymentCouponReconciliationJobConfig.JOB_NAME)
@Sql(scripts = "/schema-batch-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class CouponReconciliationJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(PaymentCouponReconciliationJobConfig.JOB_NAME)
    private Job job;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
    }

    @DisplayName("B7-3: 쿠폰 대사 배치 → 정상 실행 (데이터 없음 시에도 성공)")
    @Test
    void couponReconciliationJob_success() throws Exception {
        var jobParameters = new JobParametersBuilder()
            .addLocalDate("requestDate", LocalDate.now())
            .toJobParameters();
        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        assertThat(jobExecution).isNotNull();
        assertThat(jobExecution.getExitStatus().getExitCode())
            .isEqualTo(ExitStatus.COMPLETED.getExitCode());
    }
}
