package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.step.MonthlyActivateVersionTasklet;
import com.loopers.batch.job.ranking.step.MonthlyClearOldVersionTasklet;
import com.loopers.batch.job.ranking.step.MonthlyRankingProcessor;
import com.loopers.batch.job.ranking.step.MonthlyRankingReader;
import com.loopers.batch.job.ranking.step.MonthlyRankingWriter;
import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.ProductMetricsAggregation;
import com.loopers.domain.ranking.ProductRankMonthly;
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

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyRankingJobConfig {
    public static final String JOB_NAME = "monthlyRankingJob";
    private static final String STEP_CLEAR = "monthlyRankingClearStep";
    private static final String STEP_AGGREGATE = "monthlyRankingAggregateStep";
    private static final String STEP_ACTIVATE = "monthlyRankingActivateStep";
    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ChunkListener chunkListener;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob(
        MonthlyClearOldVersionTasklet clearOldVersionTasklet,
        MonthlyRankingReader reader,
        MonthlyRankingProcessor processor,
        MonthlyRankingWriter writer,
        MonthlyActivateVersionTasklet activateVersionTasklet
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(clearOldVersionStep(clearOldVersionTasklet))
            .next(aggregateStep(reader, processor, writer))
            .next(activateVersionStep(activateVersionTasklet))
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_CLEAR)
    public Step clearOldVersionStep(MonthlyClearOldVersionTasklet tasklet) {
        return new StepBuilder(STEP_CLEAR, jobRepository)
            .tasklet(tasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean(STEP_AGGREGATE)
    public Step aggregateStep(
        MonthlyRankingReader reader,
        MonthlyRankingProcessor processor,
        MonthlyRankingWriter writer
    ) {
        return new StepBuilder(STEP_AGGREGATE, jobRepository)
            .<ProductMetricsAggregation, ProductRankMonthly>chunk(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .listener(stepMonitorListener)
            .listener(chunkListener)
            .build();
    }

    @JobScope
    @Bean(STEP_ACTIVATE)
    public Step activateVersionStep(MonthlyActivateVersionTasklet tasklet) {
        return new StepBuilder(STEP_ACTIVATE, jobRepository)
            .tasklet(tasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }
}
