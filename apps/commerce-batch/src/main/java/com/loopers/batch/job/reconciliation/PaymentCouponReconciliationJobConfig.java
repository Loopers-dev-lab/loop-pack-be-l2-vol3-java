package com.loopers.batch.job.reconciliation;

import com.loopers.batch.job.reconciliation.step.PaymentCouponReconciliationTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = PaymentCouponReconciliationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class PaymentCouponReconciliationJobConfig {
    public static final String JOB_NAME = "paymentCouponReconciliationJob";
    private static final String STEP_NAME = "paymentCouponReconciliationStep";

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final PaymentCouponReconciliationTasklet tasklet;
    private final PlatformTransactionManager transactionManager;

    @Bean(JOB_NAME)
    public Job paymentCouponReconciliationJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(paymentCouponReconciliationStep())
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step paymentCouponReconciliationStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
            .tasklet(tasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }
}
