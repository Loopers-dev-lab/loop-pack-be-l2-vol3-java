package com.loopers.batch.job.likesync;

import com.loopers.batch.job.likesync.step.LikeCountSyncTasklet;
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

/**
 * 좋아요 카운트 동기화 Job 설정.
 * Redis 캐시 카운터 → products.like_count 컬럼으로 동기화.
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = LikeCountSyncJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class LikeCountSyncJobConfig {

    public static final String JOB_NAME = "likeCountSyncJob";
    private static final String STEP_SYNC_NAME = "likeCountSyncStep";

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final LikeCountSyncTasklet likeCountSyncTasklet;
    private final PlatformTransactionManager transactionManager;

    @Bean(JOB_NAME)
    public Job likeCountSyncJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(likeCountSyncStep())
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(STEP_SYNC_NAME)
    public Step likeCountSyncStep() {
        return new StepBuilder(STEP_SYNC_NAME, jobRepository)
                .tasklet(likeCountSyncTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }
}
