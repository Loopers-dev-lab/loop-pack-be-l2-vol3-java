package com.loopers.batch.job.ranking.weekly;

import com.loopers.batch.job.ranking.common.ProductAggregate;
import com.loopers.batch.job.ranking.common.ProductAggregateWithScore;
import com.loopers.batch.job.ranking.weekly.step.WeeklyCleanupTmpTasklet;
import com.loopers.batch.job.ranking.weekly.step.WeeklyProductMetricsReader;
import com.loopers.batch.job.ranking.weekly.step.WeeklyScoreProcessor;
import com.loopers.batch.job.ranking.weekly.step.WeeklyTmpAggregateWriter;
import com.loopers.batch.job.ranking.weekly.step.WriteWeeklyTopRanksTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 주간 랭킹 집계 Job.
 *
 * <p>실행: {@code --spring.batch.job.name=weeklyRankingJob baseDate=yyyyMMdd}</p>
 * <p>Step 구성:</p>
 * <ol>
 *     <li>Step 0 — {@link WeeklyCleanupTmpTasklet}: tmp 테이블 TRUNCATE</li>
 *     <li>Step 1 — Chunk(Reader/Processor/Writer): 7일 윈도우 집계 → tmp upsert</li>
 *     <li>Step 2 — {@link WriteWeeklyTopRanksTasklet}: tmp TOP 100 → MV 원자 교체</li>
 * </ol>
 */
@Configuration
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";

    private static final String STEP_CLEANUP = "weeklyCleanupTmpStep";
    private static final String STEP_AGGREGATE = "weeklyAggregateStep";
    private static final String STEP_WRITE_TOP = "writeWeeklyTopRanksStep";

    private static final int CHUNK_SIZE = 1000;
    private static final int RETRY_LIMIT = 3;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager txManager;
    private final DataSource dataSource;

    private final JobListener jobListener;
    private final StepMonitorListener stepListener;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob(
            Step weeklyCleanupTmpStep,
            Step weeklyAggregateStep,
            Step writeWeeklyTopRanksStep
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobListener)
                .start(weeklyCleanupTmpStep)
                .next(weeklyAggregateStep)
                .next(writeWeeklyTopRanksStep)
                .build();
    }

    @Bean(STEP_CLEANUP)
    public Step weeklyCleanupTmpStep(WeeklyCleanupTmpTasklet tasklet) {
        return new StepBuilder(STEP_CLEANUP, jobRepository)
                .tasklet(tasklet, txManager)
                .listener(stepListener)
                .build();
    }

    @Bean(STEP_AGGREGATE)
    public Step weeklyAggregateStep(
            JdbcPagingItemReader<ProductAggregate> weeklyReader,
            WeeklyScoreProcessor processor,
            JdbcBatchItemWriter<ProductAggregateWithScore> weeklyWriter
    ) {
        return new StepBuilder(STEP_AGGREGATE, jobRepository)
                .<ProductAggregate, ProductAggregateWithScore>chunk(CHUNK_SIZE, txManager)
                .reader(weeklyReader)
                .processor(processor)
                .writer(weeklyWriter)
                .faultTolerant()
                .retryLimit(RETRY_LIMIT)
                .retry(CannotAcquireLockException.class)
                .retry(TransientDataAccessResourceException.class)
                .listener(stepListener)
                .build();
    }

    @Bean(STEP_WRITE_TOP)
    public Step writeWeeklyTopRanksStep(WriteWeeklyTopRanksTasklet tasklet) {
        return new StepBuilder(STEP_WRITE_TOP, jobRepository)
                .tasklet(tasklet, txManager)
                .listener(stepListener)
                .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<ProductAggregate> weeklyReader(
            @Value("#{jobParameters['baseDate']}") String baseDateParam
    ) {
        LocalDate baseDate = LocalDate.parse(baseDateParam, DateTimeFormatter.BASIC_ISO_DATE);
        return WeeklyProductMetricsReader.create(dataSource, baseDate);
    }

    @Bean
    @StepScope
    public JdbcBatchItemWriter<ProductAggregateWithScore> weeklyWriter() {
        return WeeklyTmpAggregateWriter.create(dataSource);
    }
}
