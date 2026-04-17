package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.tasklet.RefreshWeeklyRankingTasklet;
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

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    public static final int TOP_N = 100;
    private static final String REFRESH_STEP = "refreshWeeklyRankingStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final RefreshWeeklyRankingTasklet refreshWeeklyRankingTasklet;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(refreshWeeklyRankingStep())
                .listener(jobListener)
                .build();
    }

    @Bean(REFRESH_STEP)
    @JobScope
    public Step refreshWeeklyRankingStep() {
        return new StepBuilder(REFRESH_STEP, jobRepository)
                .tasklet(refreshWeeklyRankingTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }
}
