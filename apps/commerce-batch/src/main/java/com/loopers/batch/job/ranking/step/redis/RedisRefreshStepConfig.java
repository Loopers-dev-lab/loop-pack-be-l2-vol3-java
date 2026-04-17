package com.loopers.batch.job.ranking.step.redis;

import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RedisRefreshStepConfig {

    public static final String STEP_NAME = "redisRefreshStep";

    private final JobRepository jobRepository;
    private final StepMonitorListener stepMonitorListener;
    private final RedisRefreshTasklet redisRefreshTasklet;

    @Bean(STEP_NAME)
    public Step redisRefreshStep() {
        // Redis 조작은 트랜잭션 매니저가 필요하지 않으므로 Resourceless 사용
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(redisRefreshTasklet, new ResourcelessTransactionManager())
                .listener(stepMonitorListener)
                .build();
    }
}
