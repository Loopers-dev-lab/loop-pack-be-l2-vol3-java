package com.loopers.batch.job.ranking;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.batch.job.ranking.step.RankedProductDto;
import com.loopers.batch.job.ranking.step.RankingItemProcessor;
import com.loopers.batch.job.ranking.step.WeeklyMvRankingItemWriter;
import com.loopers.batch.job.ranking.step.WeeklyRankAssignTasklet;
import com.loopers.infrastructure.metrics.ProductMetricsAggregatedDto;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingWeeklyJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class RankingWeeklyJobConfig {

    public static final String JOB_NAME = "rankingWeeklyJob";
    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final PlatformTransactionManager transactionManager;
    private final JdbcPagingItemReader<ProductMetricsAggregatedDto> productMetricsItemReader;
    private final RankingItemProcessor rankingItemProcessor;
    private final WeeklyMvRankingItemWriter weeklyMvRankingItemWriter;
    private final WeeklyRankAssignTasklet weeklyRankAssignTasklet;

    @Bean(JOB_NAME)
    public Job rankingWeeklyJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(weeklyScoreCalculationStep())
                .next(weeklyRankAssignStep())
                .listener(jobListener)
                .build();
    }

    @Bean("weeklyScoreCalculationStep")
    public Step weeklyScoreCalculationStep() {
        return new StepBuilder("weeklyScoreCalculationStep", jobRepository)
                .<ProductMetricsAggregatedDto, RankedProductDto>chunk(CHUNK_SIZE, transactionManager)
                .reader(productMetricsItemReader)
                .processor(rankingItemProcessor)
                .writer(weeklyMvRankingItemWriter)
                .listener(stepMonitorListener)
                .build();
    }

    @Bean("weeklyRankAssignStep")
    public Step weeklyRankAssignStep() {
        return new StepBuilder("weeklyRankAssignStep", jobRepository)
                .tasklet(weeklyRankAssignTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }
}
