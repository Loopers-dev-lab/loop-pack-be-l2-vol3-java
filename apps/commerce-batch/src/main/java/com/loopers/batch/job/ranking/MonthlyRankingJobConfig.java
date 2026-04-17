package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.tasklet.RefreshMonthlyRankingTasklet;
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
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    public static final int TOP_N = 100;
    private static final String REFRESH_STEP = "refreshMonthlyRankingStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final RefreshMonthlyRankingTasklet refreshMonthlyRankingTasklet;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(refreshMonthlyRankingStep())
                .listener(jobListener)
                .build();
    }

    @Bean(REFRESH_STEP)
    @JobScope
    public Step refreshMonthlyRankingStep() {
        return new StepBuilder(REFRESH_STEP, jobRepository)
                .tasklet(refreshMonthlyRankingTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }
}
