package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.step.WeeklyRankingClearTasklet;
import com.loopers.batch.job.ranking.step.WeeklyRankingItemProcessor;
import com.loopers.batch.job.ranking.step.WeeklyRankingRankTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.ranking.ProductRankWeekly;
import com.loopers.infrastructure.ranking.ProductRankWeeklyJpaRepository;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.data.RepositoryItemWriter;
import org.springframework.batch.item.data.builder.RepositoryItemWriterBuilder;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_CLEAR_NAME = "weeklyRankingClearStep";
    private static final String STEP_AGGREGATE_NAME = "weeklyRankingAggregateStep";
    private static final String STEP_RANK_NAME = "weeklyRankingRankStep";
    private static final int CHUNK_SIZE = 1000;

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;

    private final WeeklyRankingClearTasklet weeklyRankingClearTasklet;
    private final WeeklyRankingItemProcessor weeklyRankingItemProcessor;
    private final WeeklyRankingRankTasklet weeklyRankingRankTasklet;
    private final ProductRankWeeklyJpaRepository productRankWeeklyJpaRepository;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(weeklyRankingClearStep())
            .next(weeklyRankingAggregateStep())
            .next(weeklyRankingRankStep())
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_CLEAR_NAME)
    public Step weeklyRankingClearStep() {
        return new StepBuilder(STEP_CLEAR_NAME, jobRepository)
            .tasklet(weeklyRankingClearTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean(STEP_AGGREGATE_NAME)
    public Step weeklyRankingAggregateStep() {
        return new StepBuilder(STEP_AGGREGATE_NAME, jobRepository)
            .<ProductMetrics, ProductRankWeekly>chunk(CHUNK_SIZE, transactionManager)
            .reader(productMetricsReader())
            .processor(weeklyRankingItemProcessor)
            .writer(productRankWeeklyWriter())
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean(STEP_RANK_NAME)
    public Step weeklyRankingRankStep() {
        return new StepBuilder(STEP_RANK_NAME, jobRepository)
            .tasklet(weeklyRankingRankTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    private JpaPagingItemReader<ProductMetrics> productMetricsReader() {
        return new JpaPagingItemReaderBuilder<ProductMetrics>()
            .name("productMetricsReader")
            .entityManagerFactory(entityManagerFactory)
            .queryString("SELECT p FROM ProductMetrics p")
            .pageSize(CHUNK_SIZE)
            .build();
    }

    private RepositoryItemWriter<ProductRankWeekly> productRankWeeklyWriter() {
        return new RepositoryItemWriterBuilder<ProductRankWeekly>()
            .repository(productRankWeeklyJpaRepository)
            .methodName("save")
            .build();
    }
}
