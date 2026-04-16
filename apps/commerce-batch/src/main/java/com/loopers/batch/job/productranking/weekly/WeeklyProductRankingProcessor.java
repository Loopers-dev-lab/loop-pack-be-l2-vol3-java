package com.loopers.batch.job.productranking.weekly;


import com.loopers.batch.job.productranking.weekly.dto.ProductRankAggregate;
import com.loopers.batch.job.productranking.weekly.dto.StagingWeeklyProductRankRow;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;


/**
 * 주간 랭킹 Processor
 * - ProductRankAggregate → StagingWeeklyProductRankRow 변환
 * - 순위는 Reader SQL의 ROW_NUMBER() 결과를 그대로 사용 (Processor 재계산 금지)
 * - AtomicInteger 순위 부여 금지: Chunk 재시작/skip/retry 시 순번이 깨질 수 있음
 */

@StepScope
@Component
public class WeeklyProductRankingProcessor
	implements ItemProcessor<ProductRankAggregate, StagingWeeklyProductRankRow> {

	@Value("#{jobParameters['targetDate']}")
	private String targetDate;

	@Value("#{jobParameters['scorerType'] ?: 'SATURATION'}")
	private String scorerType;

	@Value("#{stepExecution.jobExecution.id}")
	private Long jobExecutionId;


	@Override
	public StagingWeeklyProductRankRow process(ProductRankAggregate item) {
		LocalDate target = LocalDate.parse(targetDate);
		LocalDate weekStart = target.with(DayOfWeek.MONDAY);
		LocalDate weekEnd = target.with(DayOfWeek.SUNDAY);

		return StagingWeeklyProductRankRow.from(item, weekStart, weekEnd, scorerType, jobExecutionId);
	}

}
