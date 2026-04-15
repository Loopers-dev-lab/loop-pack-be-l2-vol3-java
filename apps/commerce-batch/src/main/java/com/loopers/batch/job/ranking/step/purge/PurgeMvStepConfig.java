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

    public static final String STEP_LAST_7D  = "purgeLast7dMvStep";
    public static final String STEP_LAST_30D = "purgeLast30dMvStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final StepMonitorListener stepMonitorListener;
    private final PurgeLast7dMvTasklet purgeLast7dMvTasklet;
    private final PurgeLast30dMvTasklet purgeLast30dMvTasklet;

    @Bean(STEP_LAST_7D)
    public Step purgeLast7dMvStep() {
        return new StepBuilder(STEP_LAST_7D, jobRepository)
                .tasklet(purgeLast7dMvTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    @Bean(STEP_LAST_30D)
    public Step purgeLast30dMvStep() {
        return new StepBuilder(STEP_LAST_30D, jobRepository)
                .tasklet(purgeLast30dMvTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }
}
