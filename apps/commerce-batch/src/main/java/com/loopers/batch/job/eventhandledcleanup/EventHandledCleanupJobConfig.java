package com.loopers.batch.job.eventhandledcleanup;

import com.loopers.batch.job.eventhandledcleanup.step.EventHandledCleanupTasklet;
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

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = EventHandledCleanupJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class EventHandledCleanupJobConfig {
    public static final String JOB_NAME = "eventHandledCleanupJob";
    private static final String STEP_NAME = "eventHandledCleanupStep";

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final EventHandledCleanupTasklet eventHandledCleanupTasklet;
    private final PlatformTransactionManager transactionManager;

    @Bean(JOB_NAME)
    public Job eventHandledCleanupJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(eventHandledCleanupStep())
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step eventHandledCleanupStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
            .tasklet(eventHandledCleanupTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }
}
