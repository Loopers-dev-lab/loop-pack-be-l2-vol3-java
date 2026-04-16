package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.step.MonthlyRankingClearTasklet;
import com.loopers.batch.job.ranking.step.MonthlyRankingItemProcessor;
import com.loopers.batch.job.ranking.step.MonthlyRankingRankTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.ranking.ProductRankMonthly;
import com.loopers.infrastructure.ranking.ProductRankMonthlyJpaRepository;
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

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    private static final String STEP_CLEAR_NAME = "monthlyRankingClearStep";
    private static final String STEP_AGGREGATE_NAME = "monthlyRankingAggregateStep";
    private static final String STEP_RANK_NAME = "monthlyRankingRankStep";
    private static final int CHUNK_SIZE = 1000;

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;

    private final MonthlyRankingClearTasklet monthlyRankingClearTasklet;
    private final MonthlyRankingItemProcessor monthlyRankingItemProcessor;
    private final MonthlyRankingRankTasklet monthlyRankingRankTasklet;
    private final ProductRankMonthlyJpaRepository productRankMonthlyJpaRepository;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(monthlyRankingClearStep())
            .next(monthlyRankingAggregateStep())
            .next(monthlyRankingRankStep())
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_CLEAR_NAME)
    public Step monthlyRankingClearStep() {
        return new StepBuilder(STEP_CLEAR_NAME, jobRepository)
            .tasklet(monthlyRankingClearTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean(STEP_AGGREGATE_NAME)
    public Step monthlyRankingAggregateStep() {
        return new StepBuilder(STEP_AGGREGATE_NAME, jobRepository)
            .<ProductMetrics, ProductRankMonthly>chunk(CHUNK_SIZE, transactionManager)
            .reader(productMetricsReader())
            .processor(monthlyRankingItemProcessor)
            .writer(productRankMonthlyWriter())
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean(STEP_RANK_NAME)
    public Step monthlyRankingRankStep() {
        return new StepBuilder(STEP_RANK_NAME, jobRepository)
            .tasklet(monthlyRankingRankTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    private JpaPagingItemReader<ProductMetrics> productMetricsReader() {
        return new JpaPagingItemReaderBuilder<ProductMetrics>()
            .name("monthlyProductMetricsReader")
            .entityManagerFactory(entityManagerFactory)
            .queryString("SELECT p FROM ProductMetrics p")
            .pageSize(CHUNK_SIZE)
            .build();
    }

    private RepositoryItemWriter<ProductRankMonthly> productRankMonthlyWriter() {
        return new RepositoryItemWriterBuilder<ProductRankMonthly>()
            .repository(productRankMonthlyJpaRepository)
            .methodName("save")
            .build();
    }
}
