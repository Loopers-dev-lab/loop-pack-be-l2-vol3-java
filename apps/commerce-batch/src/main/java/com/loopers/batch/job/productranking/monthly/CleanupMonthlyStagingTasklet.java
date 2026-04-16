package com.loopers.batch.job.productranking.monthly;


import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;


/**
 * 월간 랭킹 Staging 정리 Tasklet (Step 1)
 * - 동일 period + scorerType의 이전 staging row 삭제
 */

@StepScope
@Component
@Slf4j
public class CleanupMonthlyStagingTasklet implements Tasklet {

	// jdbc
	private final JdbcTemplate jdbcTemplate;

	@Value("#{jobParameters['targetDate']}")
	private String targetDate;

	@Value("#{jobParameters['scorerType'] ?: 'SATURATION'}")
	private String scorerType;


	public CleanupMonthlyStagingTasklet(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}


	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		LocalDate target = LocalDate.parse(targetDate);
		LocalDate monthStart = target.withDayOfMonth(1);

		int deleted = jdbcTemplate.update(
			"DELETE FROM stg_product_rank_monthly WHERE month_start_date = ? AND scorer_type = ?",
			Date.valueOf(monthStart), scorerType
		);

		log.info("[CleanupMonthlyStaging] month_start={}, scorerType={}, 삭제 {}건",
			monthStart, scorerType, deleted);

		contribution.incrementWriteCount(deleted);

		return RepeatStatus.FINISHED;
	}

}
