package com.loopers.batch.ranking.monthly;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.ranking.RankingAggregateRow;
import com.loopers.batch.ranking.RankingLatestDateCacheListener;
import com.loopers.domain.rank.MvProductRankMonthly;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = "monthlyRankingJob", matchIfMissing = true)
@RequiredArgsConstructor
@Configuration
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    private static final String STEP_NAME = "monthlyRankingStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob(Step monthlyRankingStep, RankingLatestDateCacheListener cacheListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .start(monthlyRankingStep)
            .listener(jobListener)
            .listener(cacheListener)
            .build();
    }

    @Bean(STEP_NAME)
    public Step monthlyRankingStep(JdbcCursorItemReader<RankingAggregateRow> monthlyRankReader,
                                   MonthlyRankProcessor monthlyRankProcessor,
                                   MonthlyRankWriter monthlyRankWriter) {
        return new StepBuilder(STEP_NAME, jobRepository)
            .<RankingAggregateRow, MvProductRankMonthly>chunk(50, transactionManager)
            .reader(monthlyRankReader)
            .processor(monthlyRankProcessor)
            .writer(monthlyRankWriter)
            .build();
    }
}
