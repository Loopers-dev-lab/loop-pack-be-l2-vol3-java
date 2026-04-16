package com.loopers.batch.job.productranking.monthly;


import com.loopers.batch.job.productranking.monthly.dto.StagingMonthlyProductRankRow;
import com.loopers.batch.job.productranking.weekly.dto.ProductRankAggregate;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;


/**
 * 월간 랭킹 Job 설정
 * - Step 1: cleanupMonthlyStagingStep (Tasklet)
 * - Step 2: aggregateMonthlyStep (Chunk)
 * - Step 3: publishMonthlyMvStep (Tasklet)
 *
 * JobParameters:
 * - targetDate (필수): yyyy-MM-dd
 * - scorerType (선택, 기본 SATURATION)
 */

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyProductRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyProductRankingJobConfig {

	public static final String JOB_NAME = "monthlyProductRankingJob";
	private static final String CLEANUP_STEP = "cleanupMonthlyStagingStep";
	private static final String AGGREGATE_STEP = "aggregateMonthlyStep";
	private static final String PUBLISH_STEP = "publishMonthlyMvStep";

	// batch infra
	private final JobRepository jobRepository;
	private final PlatformTransactionManager transactionManager;
	// listeners
	private final JobListener jobListener;
	private final StepMonitorListener stepMonitorListener;
	// steps
	private final CleanupMonthlyStagingTasklet cleanupMonthlyStagingTasklet;
	private final MonthlyProductRankingProcessor monthlyProductRankingProcessor;
	private final PublishMonthlyMvTasklet publishMonthlyMvTasklet;


	@Bean(JOB_NAME)
	public Job monthlyProductRankingJob(
		@Qualifier(CLEANUP_STEP) Step cleanupStep,
		@Qualifier(AGGREGATE_STEP) Step aggregateStep,
		@Qualifier(PUBLISH_STEP) Step publishStep
	) {
		return new JobBuilder(JOB_NAME, jobRepository)
			.start(cleanupStep)
			.next(aggregateStep)
			.next(publishStep)
			.listener(jobListener)
			.build();
	}


	@Bean(CLEANUP_STEP)
	@JobScope
	public Step cleanupMonthlyStagingStep() {
		return new StepBuilder(CLEANUP_STEP, jobRepository)
			.tasklet(cleanupMonthlyStagingTasklet, transactionManager)
			.listener(stepMonitorListener)
			.build();
	}


	@Bean(AGGREGATE_STEP)
	@JobScope
	public Step aggregateMonthlyStep(
		@Qualifier("monthlyProductRankingReader") JdbcCursorItemReader<ProductRankAggregate> reader,
		@Qualifier("monthlyProductRankingWriter") JdbcBatchItemWriter<StagingMonthlyProductRankRow> writer
	) {
		return new StepBuilder(AGGREGATE_STEP, jobRepository)
			.<ProductRankAggregate, StagingMonthlyProductRankRow>chunk(100, transactionManager)
			.reader(reader)
			.processor(monthlyProductRankingProcessor)
			.writer(writer)
			.listener(stepMonitorListener)
			.build();
	}


	@Bean(PUBLISH_STEP)
	@JobScope
	public Step publishMonthlyMvStep() {
		return new StepBuilder(PUBLISH_STEP, jobRepository)
			.tasklet(publishMonthlyMvTasklet, transactionManager)
			.listener(stepMonitorListener)
			.build();
	}

}
