package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.dto.DailyLedgerRow;
import com.loopers.batch.job.ranking.dto.StagingDelta;
import com.loopers.batch.job.ranking.step.DeleteWeeklyStagingTasklet;
import com.loopers.batch.job.ranking.step.LedgerToStagingProcessor;
import com.loopers.batch.job.ranking.step.WeeklyStagingToMvTasklet;
import com.loopers.batch.job.ranking.step.WeeklyStagingUpsertWriter;
import com.loopers.batch.job.ranking.validator.WeeklyJobParametersValidator;
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
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@Configuration
@RequiredArgsConstructor
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_DELETE_STAGING = "weeklyRankingStep0DeleteStaging";
    private static final String STEP_LEDGER_TO_STAGING = "weeklyRankingStep1LedgerToStaging";
    private static final String STEP_STAGING_TO_MV = "weeklyRankingStep2StagingToMv";

    private static final int CHUNK_SIZE = 1000;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final DeleteWeeklyStagingTasklet deleteWeeklyStagingTasklet;
    private final LedgerToStagingProcessor ledgerToStagingProcessor;
    private final WeeklyStagingUpsertWriter weeklyStagingUpsertWriter;
    private final WeeklyStagingToMvTasklet weeklyStagingToMvTasklet;
    private final WeeklyJobParametersValidator weeklyJobParametersValidator;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .validator(weeklyJobParametersValidator)
            .start(weeklyRankingStep0DeleteStaging())
            .next(weeklyRankingStep1LedgerToStaging(null))
            .next(weeklyRankingStep2StagingToMv())
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_DELETE_STAGING)
    public Step weeklyRankingStep0DeleteStaging() {
        return new StepBuilder(STEP_DELETE_STAGING, jobRepository)
            .tasklet(deleteWeeklyStagingTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean(STEP_LEDGER_TO_STAGING)
    public Step weeklyRankingStep1LedgerToStaging(
        @Qualifier("weeklyLedgerReader") ItemReader<DailyLedgerRow> weeklyLedgerReader
    ) {
        return new StepBuilder(STEP_LEDGER_TO_STAGING, jobRepository)
            .<DailyLedgerRow, StagingDelta>chunk(CHUNK_SIZE, transactionManager)
            .reader(weeklyLedgerReader)
            .processor(ledgerToStagingProcessor)
            .writer(weeklyStagingUpsertWriter)
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean(STEP_STAGING_TO_MV)
    public Step weeklyRankingStep2StagingToMv() {
        return new StepBuilder(STEP_STAGING_TO_MV, jobRepository)
            .tasklet(weeklyStagingToMvTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }
}
