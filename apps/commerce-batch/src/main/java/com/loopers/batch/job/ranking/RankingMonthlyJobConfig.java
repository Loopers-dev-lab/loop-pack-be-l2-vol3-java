package com.loopers.batch.job.ranking;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.batch.job.ranking.step.MonthlyMvRankingItemWriter;
import com.loopers.batch.job.ranking.step.MonthlyRankAssignTasklet;
import com.loopers.batch.job.ranking.step.ProductMetricsItemReader;
import com.loopers.batch.job.ranking.step.RankedProductDto;
import com.loopers.batch.job.ranking.step.RankingItemProcessor;
import com.loopers.infrastructure.metrics.ProductMetricsAggregatedDto;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingMonthlyJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class RankingMonthlyJobConfig {

    public static final String JOB_NAME = "rankingMonthlyJob";
    public static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final PlatformTransactionManager transactionManager;
    private final ProductMetricsItemReader productMetricsItemReader;
    private final RankingItemProcessor rankingItemProcessor;
    private final MonthlyMvRankingItemWriter monthlyMvRankingItemWriter;
    private final MonthlyRankAssignTasklet monthlyRankAssignTasklet;

    @Bean(JOB_NAME)
    public Job rankingMonthlyJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(monthlyScoreCalculationStep())
                .next(monthlyRankAssignStep())
                .listener(jobListener)
                .build();
    }

    @Bean("monthlyScoreCalculationStep")
    public Step monthlyScoreCalculationStep() {
        return new StepBuilder("monthlyScoreCalculationStep", jobRepository)
                .<ProductMetricsAggregatedDto, RankedProductDto>chunk(CHUNK_SIZE, transactionManager)
                .reader(productMetricsItemReader.reader(null, null, null))
                .processor(rankingItemProcessor)
                .writer(monthlyMvRankingItemWriter)
                .listener(stepMonitorListener)
                .build();
    }

    @Bean("monthlyRankAssignStep")
    public Step monthlyRankAssignStep() {
        return new StepBuilder("monthlyRankAssignStep", jobRepository)
                .tasklet(monthlyRankAssignTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }
}
