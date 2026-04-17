package com.loopers.batch.job.metricsreconcile;

import com.loopers.batch.job.metricsreconcile.step.MetricsReconcileTasklet;
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

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MetricsReconcileJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MetricsReconcileJobConfig {
    public static final String JOB_NAME = "metricsReconcileJob";
    private static final String STEP_NAME = "metricsReconcileStep";

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final MetricsReconcileTasklet metricsReconcileTasklet;
    private final PlatformTransactionManager transactionManager;

    @Bean(JOB_NAME)
    public Job metricsReconcileJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(metricsReconcileStep())
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step metricsReconcileStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
            .tasklet(metricsReconcileTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }
}
