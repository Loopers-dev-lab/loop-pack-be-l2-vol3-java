package com.loopers.batch.ranking.weekly;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.ranking.RankingAggregateRow;
import com.loopers.batch.ranking.RankingLatestDateCacheListener;
import com.loopers.domain.rank.MvProductRankWeekly;
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

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = "weeklyRankingJob", matchIfMissing = true)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_NAME = "weeklyRankingStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob(Step weeklyRankingStep, RankingLatestDateCacheListener cacheListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .start(weeklyRankingStep)
            .listener(jobListener)
            .listener(cacheListener)
            .build();
    }

    @Bean(STEP_NAME)
    public Step weeklyRankingStep(JdbcCursorItemReader<RankingAggregateRow> weeklyRankReader,
                                  WeeklyRankProcessor weeklyRankProcessor,
                                  WeeklyRankWriter weeklyRankWriter) {
        return new StepBuilder(STEP_NAME, jobRepository)
            .<RankingAggregateRow, MvProductRankWeekly>chunk(50, transactionManager)
            .reader(weeklyRankReader)
            .processor(weeklyRankProcessor)
            .writer(weeklyRankWriter)
            .build();
    }
}
