package com.loopers.batch.job.ranking.monthly;

import com.loopers.batch.job.ranking.common.ProductAggregate;
import com.loopers.batch.job.ranking.common.ProductAggregateWithScore;
import com.loopers.batch.job.ranking.monthly.step.MonthlyCleanupTmpTasklet;
import com.loopers.batch.job.ranking.monthly.step.MonthlyProductMetricsReader;
import com.loopers.batch.job.ranking.monthly.step.MonthlyScoreProcessor;
import com.loopers.batch.job.ranking.monthly.step.MonthlyTmpAggregateWriter;
import com.loopers.batch.job.ranking.monthly.step.WriteMonthlyTopRanksTasklet;
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
 * 월간 랭킹 집계 Job.
 *
 * <p>실행: {@code --spring.batch.job.name=monthlyRankingJob baseDate=yyyyMMdd}</p>
 * <p>주간 Job과 구조 동일. 기간 계산(monthStart~baseDate)과 MV 키(year_month)만 다름.</p>
 */
@Configuration
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";

    private static final String STEP_CLEANUP = "monthlyCleanupTmpStep";
    private static final String STEP_AGGREGATE = "monthlyAggregateStep";
    private static final String STEP_WRITE_TOP = "writeMonthlyTopRanksStep";

    private static final int CHUNK_SIZE = 1000;
    private static final int RETRY_LIMIT = 3;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager txManager;
    private final DataSource dataSource;

    private final JobListener jobListener;
    private final StepMonitorListener stepListener;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob(
            Step monthlyCleanupTmpStep,
            Step monthlyAggregateStep,
            Step writeMonthlyTopRanksStep
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobListener)
                .start(monthlyCleanupTmpStep)
                .next(monthlyAggregateStep)
                .next(writeMonthlyTopRanksStep)
                .build();
    }

    @Bean(STEP_CLEANUP)
    public Step monthlyCleanupTmpStep(MonthlyCleanupTmpTasklet tasklet) {
        return new StepBuilder(STEP_CLEANUP, jobRepository)
                .tasklet(tasklet, txManager)
                .listener(stepListener)
                .build();
    }

    @Bean(STEP_AGGREGATE)
    public Step monthlyAggregateStep(
            JdbcPagingItemReader<ProductAggregate> monthlyReader,
            MonthlyScoreProcessor processor,
            JdbcBatchItemWriter<ProductAggregateWithScore> monthlyWriter
    ) {
        return new StepBuilder(STEP_AGGREGATE, jobRepository)
                .<ProductAggregate, ProductAggregateWithScore>chunk(CHUNK_SIZE, txManager)
                .reader(monthlyReader)
                .processor(processor)
                .writer(monthlyWriter)
                .faultTolerant()
                .retryLimit(RETRY_LIMIT)
                .retry(CannotAcquireLockException.class)
                .retry(TransientDataAccessResourceException.class)
                .listener(stepListener)
                .build();
    }

    @Bean(STEP_WRITE_TOP)
    public Step writeMonthlyTopRanksStep(WriteMonthlyTopRanksTasklet tasklet) {
        return new StepBuilder(STEP_WRITE_TOP, jobRepository)
                .tasklet(tasklet, txManager)
                .listener(stepListener)
                .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<ProductAggregate> monthlyReader(
            @Value("#{jobParameters['baseDate']}") String baseDateParam
    ) {
        LocalDate baseDate = LocalDate.parse(baseDateParam, DateTimeFormatter.BASIC_ISO_DATE);
        return MonthlyProductMetricsReader.create(dataSource, baseDate);
    }

    @Bean
    @StepScope
    public JdbcBatchItemWriter<ProductAggregateWithScore> monthlyWriter() {
        return MonthlyTmpAggregateWriter.create(dataSource);
    }
}
