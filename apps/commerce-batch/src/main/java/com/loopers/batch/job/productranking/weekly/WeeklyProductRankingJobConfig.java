package com.loopers.batch.job.productranking.weekly;


import com.loopers.batch.job.productranking.weekly.dto.ProductRankAggregate;
import com.loopers.batch.job.productranking.weekly.dto.StagingWeeklyProductRankRow;
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
 * 주간 랭킹 Job 설정
 * - Step 1: cleanupWeeklyStagingStep (Tasklet) — 이전 staging rows 정리
 * - Step 2: aggregateWeeklyStep (Chunk) — product_metrics 집계 → staging 적재
 * - Step 3: publishWeeklyMvStep (Tasklet) — staging → mv_product_rank_weekly 교체
 *
 * JobParameters:
 * - targetDate (필수): yyyy-MM-dd, 이 날짜가 속한 주를 재계산
 * - scorerType (선택, 기본 SATURATION)
 * - run.id (선택): 완료된 동일 파라미터 재실행 시
 */

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyProductRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyProductRankingJobConfig {

	public static final String JOB_NAME = "weeklyProductRankingJob";
	private static final String CLEANUP_STEP = "cleanupWeeklyStagingStep";
	private static final String AGGREGATE_STEP = "aggregateWeeklyStep";
	private static final String PUBLISH_STEP = "publishWeeklyMvStep";

	// batch infra
	private final JobRepository jobRepository;
	private final PlatformTransactionManager transactionManager;
	// listeners
	private final JobListener jobListener;
	private final StepMonitorListener stepMonitorListener;
	// steps
	private final CleanupWeeklyStagingTasklet cleanupWeeklyStagingTasklet;
	private final WeeklyProductRankingProcessor weeklyProductRankingProcessor;
	private final PublishWeeklyMvTasklet publishWeeklyMvTasklet;


	@Bean(JOB_NAME)
	public Job weeklyProductRankingJob(
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
	public Step cleanupWeeklyStagingStep() {
		return new StepBuilder(CLEANUP_STEP, jobRepository)
			.tasklet(cleanupWeeklyStagingTasklet, transactionManager)
			.listener(stepMonitorListener)
			.build();
	}


	@Bean(AGGREGATE_STEP)
	@JobScope
	public Step aggregateWeeklyStep(
		@Qualifier("weeklyProductRankingReader") JdbcCursorItemReader<ProductRankAggregate> reader,
		@Qualifier("weeklyProductRankingWriter") JdbcBatchItemWriter<StagingWeeklyProductRankRow> writer
	) {
		return new StepBuilder(AGGREGATE_STEP, jobRepository)
			.<ProductRankAggregate, StagingWeeklyProductRankRow>chunk(100, transactionManager)
			.reader(reader)
			.processor(weeklyProductRankingProcessor)
			.writer(writer)
			.listener(stepMonitorListener)
			.build();
	}


	@Bean(PUBLISH_STEP)
	@JobScope
	public Step publishWeeklyMvStep() {
		return new StepBuilder(PUBLISH_STEP, jobRepository)
			.tasklet(publishWeeklyMvTasklet, transactionManager)
			.listener(stepMonitorListener)
			.build();
	}

}
