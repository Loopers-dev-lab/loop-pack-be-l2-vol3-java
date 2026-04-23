package com.loopers.batch.job.ranking.weekly;

import com.loopers.batch.job.ranking.ScoredProductMetrics;
import com.loopers.batch.job.ranking.weekly.step.WeeklyRankingProcessor;
import com.loopers.batch.job.ranking.weekly.step.WeeklyRankingReader;
import com.loopers.batch.job.ranking.weekly.step.WeeklyRankingWriter;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.BatchProductMetricsModel;
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

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_NAME = "weeklyRankingStep";
    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final WeeklyRankingReader weeklyRankingReader;
    private final WeeklyRankingProcessor weeklyRankingProcessor;
    private final WeeklyRankingWriter weeklyRankingWriter;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(weeklyRankingStep())
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step weeklyRankingStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
            .<BatchProductMetricsModel, ScoredProductMetrics>chunk(CHUNK_SIZE, transactionManager)
            .reader(weeklyRankingReader.reader())
            .processor(weeklyRankingProcessor)
            .writer(weeklyRankingWriter)
            .listener(stepMonitorListener)
            .listener(weeklyRankingWriter)
            .build();
    }
}
