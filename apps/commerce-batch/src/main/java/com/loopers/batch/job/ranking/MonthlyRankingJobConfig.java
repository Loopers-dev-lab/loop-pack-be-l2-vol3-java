package com.loopers.batch.job.ranking;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.loopers.batch.job.ranking.step.MonthlyRankingTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;

import lombok.RequiredArgsConstructor;

/**
 * 월간 랭킹 배치 Job 설정.
 *
 * <p>{@code spring.batch.job.name=monthlyRankingJob}으로 실행하며,
 * JobParameters로 {@code date}(yyyyMMdd)를 전달받는다. 해당 날짜 기준 직전 30일의 {@code product_metrics}를 집계하여 {@code mv_product_rank_monthly}
 * 테이블에 upsert한다.</p>
 *
 * <p>실행 예시:</p>
 * <pre>
 * ./gradlew :apps:commerce-batch:bootRun \
 *   --args="--spring.batch.job.name=monthlyRankingJob date=20260414"
 * </pre>
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    private static final String STEP_NAME = "monthlyRankingStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final MonthlyRankingTasklet monthlyRankingTasklet;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(monthlyRankingStep())
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step monthlyRankingStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .tasklet(monthlyRankingTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }
}
