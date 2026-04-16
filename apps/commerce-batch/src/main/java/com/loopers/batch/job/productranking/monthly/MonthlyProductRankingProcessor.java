package com.loopers.batch.job.productranking.monthly;


import com.loopers.batch.job.productranking.monthly.dto.StagingMonthlyProductRankRow;
import com.loopers.batch.job.productranking.weekly.dto.ProductRankAggregate;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


/**
 * 월간 랭킹 Processor
 * - ProductRankAggregate → StagingMonthlyProductRankRow 변환
 * - 순위는 Reader SQL의 ROW_NUMBER() 결과를 그대로 사용
 */

@StepScope
@Component
public class MonthlyProductRankingProcessor
	implements ItemProcessor<ProductRankAggregate, StagingMonthlyProductRankRow> {

	private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

	@Value("#{jobParameters['targetDate']}")
	private String targetDate;

	@Value("#{jobParameters['scorerType'] ?: 'SATURATION'}")
	private String scorerType;

	@Value("#{stepExecution.jobExecution.id}")
	private Long jobExecutionId;


	@Override
	public StagingMonthlyProductRankRow process(ProductRankAggregate item) {
		LocalDate target = LocalDate.parse(targetDate);
		LocalDate monthStart = target.withDayOfMonth(1);
		String monthKey = target.format(YEAR_MONTH_FORMAT);

		return StagingMonthlyProductRankRow.from(item, monthStart, monthKey, scorerType, jobExecutionId);
	}

}
