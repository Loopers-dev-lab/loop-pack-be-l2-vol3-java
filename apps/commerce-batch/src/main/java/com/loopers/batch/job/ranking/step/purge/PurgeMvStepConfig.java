package com.loopers.batch.job.ranking.step.purge;

import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class PurgeMvStepConfig {

    public static final String STEP_NAME = "purgeMvStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final StepMonitorListener stepMonitorListener;
    private final PurgeMvTasklet purgeMvTasklet;

    @Bean(STEP_NAME)
    public Step purgeMvStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(purgeMvTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }
}
