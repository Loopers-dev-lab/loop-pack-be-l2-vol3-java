package com.loopers.batch.job.cleanup;

import com.loopers.batch.job.cleanup.step.StagingCleanupTasklet;
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

@ConditionalOnProperty(
    name = "spring.batch.job.name",
    havingValue = StagingCleanupJobConfig.JOB_NAME
)
@Configuration
@RequiredArgsConstructor
public class StagingCleanupJobConfig {

    public static final String JOB_NAME = "rankingStagingCleanupJob";
    private static final String STEP_NAME = "rankingStagingCleanupStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final StagingCleanupTasklet stagingCleanupTasklet;

    @Bean(JOB_NAME)
    public Job rankingStagingCleanupJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(rankingStagingCleanupStep())
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step rankingStagingCleanupStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
            .tasklet(stagingCleanupTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }
}
