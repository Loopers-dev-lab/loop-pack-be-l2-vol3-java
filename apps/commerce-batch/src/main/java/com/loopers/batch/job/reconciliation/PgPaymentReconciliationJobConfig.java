package com.loopers.batch.job.reconciliation;

import com.loopers.batch.job.reconciliation.step.PgPaymentReconciliationTasklet;
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

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = PgPaymentReconciliationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class PgPaymentReconciliationJobConfig {
    public static final String JOB_NAME = "pgPaymentReconciliationJob";
    private static final String STEP_NAME = "pgPaymentReconciliationStep";

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final PgPaymentReconciliationTasklet tasklet;
    private final PlatformTransactionManager transactionManager;

    @Bean(JOB_NAME)
    public Job pgPaymentReconciliationJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(pgPaymentReconciliationStep())
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step pgPaymentReconciliationStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
            .tasklet(tasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }
}
