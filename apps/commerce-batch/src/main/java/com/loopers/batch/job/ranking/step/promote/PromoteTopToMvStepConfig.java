package com.loopers.batch.job.ranking.step.promote;

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
public class PromoteTopToMvStepConfig {

    public static final String STEP_NAME = "promoteTopToMvStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final StepMonitorListener stepMonitorListener;
    private final PromoteTopToMvTasklet promoteTopToMvTasklet;

    @Bean(STEP_NAME)
    public Step promoteTopToMvStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(promoteTopToMvTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }
}
