package com.loopers.batch.job.productranking.weekly;


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
import java.time.DayOfWeek;
import java.time.LocalDate;


/**
 * 주간 랭킹 Staging 정리 Tasklet (Step 1)
 * - 동일 period + scorerType의 이전 staging row 삭제
 * - 완료된 Step은 재시작 시 건너뜀 → publish 실패 후 재시작 시 staging 보존
 */

@StepScope
@Component
@Slf4j
public class CleanupWeeklyStagingTasklet implements Tasklet {

	// jdbc
	private final JdbcTemplate jdbcTemplate;

	@Value("#{jobParameters['targetDate']}")
	private String targetDate;

	@Value("#{jobParameters['scorerType'] ?: 'SATURATION'}")
	private String scorerType;


	public CleanupWeeklyStagingTasklet(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}


	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		// targetDate가 속한 주의 월요일 계산
		LocalDate target = LocalDate.parse(targetDate);
		LocalDate weekStart = target.with(DayOfWeek.MONDAY);

		// 동일 period + scorerType의 stale staging rows 삭제
		int deleted = jdbcTemplate.update(
			"DELETE FROM stg_product_rank_weekly WHERE week_start_date = ? AND scorer_type = ?",
			Date.valueOf(weekStart), scorerType
		);

		log.info("[CleanupWeeklyStaging] week_start={}, scorerType={}, 삭제 {}건",
			weekStart, scorerType, deleted);

		contribution.incrementWriteCount(deleted);

		return RepeatStatus.FINISHED;
	}

}
