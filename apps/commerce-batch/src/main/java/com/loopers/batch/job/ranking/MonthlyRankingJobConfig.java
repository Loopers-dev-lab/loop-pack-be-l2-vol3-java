package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.dto.DailyLedgerRow;
import com.loopers.batch.job.ranking.dto.StagingDelta;
import com.loopers.batch.job.ranking.step.DeleteMonthlyStagingTasklet;
import com.loopers.batch.job.ranking.step.LedgerToStagingProcessor;
import com.loopers.batch.job.ranking.step.MonthlyStagingToMvTasklet;
import com.loopers.batch.job.ranking.step.MonthlyStagingUpsertWriter;
import com.loopers.batch.job.ranking.validator.MonthlyJobParametersValidator;
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

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@Configuration
@RequiredArgsConstructor
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    private static final String STEP_DELETE_STAGING = "monthlyRankingStep0DeleteStaging";
    private static final String STEP_LEDGER_TO_STAGING = "monthlyRankingStep1LedgerToStaging";
    private static final String STEP_STAGING_TO_MV = "monthlyRankingStep2StagingToMv";

    private static final int CHUNK_SIZE = 1000;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final DeleteMonthlyStagingTasklet deleteMonthlyStagingTasklet;
    private final LedgerToStagingProcessor ledgerToStagingProcessor;
    private final MonthlyStagingUpsertWriter monthlyStagingUpsertWriter;
    private final MonthlyStagingToMvTasklet monthlyStagingToMvTasklet;
    private final MonthlyJobParametersValidator monthlyJobParametersValidator;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .validator(monthlyJobParametersValidator)
            .start(monthlyRankingStep0DeleteStaging())
            .next(monthlyRankingStep1LedgerToStaging(null))
            .next(monthlyRankingStep2StagingToMv())
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_DELETE_STAGING)
    public Step monthlyRankingStep0DeleteStaging() {
        return new StepBuilder(STEP_DELETE_STAGING, jobRepository)
            .tasklet(deleteMonthlyStagingTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean(STEP_LEDGER_TO_STAGING)
    public Step monthlyRankingStep1LedgerToStaging(
        @Qualifier("monthlyLedgerReader") ItemReader<DailyLedgerRow> monthlyLedgerReader
    ) {
        return new StepBuilder(STEP_LEDGER_TO_STAGING, jobRepository)
            .<DailyLedgerRow, StagingDelta>chunk(CHUNK_SIZE, transactionManager)
            .reader(monthlyLedgerReader)
            .processor(ledgerToStagingProcessor)
            .writer(monthlyStagingUpsertWriter)
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean(STEP_STAGING_TO_MV)
    public Step monthlyRankingStep2StagingToMv() {
        return new StepBuilder(STEP_STAGING_TO_MV, jobRepository)
            .tasklet(monthlyStagingToMvTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }
}
