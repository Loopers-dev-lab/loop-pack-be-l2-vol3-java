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
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;


/**
 * 주간 랭킹 MV Publish Tasklet (Step 3)
 * - 단일 트랜잭션: MV DELETE → staging INSERT → staging 정리
 * - publish 트랜잭션이 롤백되면 기존 MV 유지 (API 빈 응답 방지)
 */

@StepScope
@Component
@Slf4j
public class PublishWeeklyMvTasklet implements Tasklet {

	// jdbc
	private final JdbcTemplate jdbcTemplate;

	@Value("#{jobParameters['targetDate']}")
	private String targetDate;

	@Value("#{jobParameters['scorerType'] ?: 'SATURATION'}")
	private String scorerType;

	@Value("#{stepExecution.jobExecution.id}")
	private Long jobExecutionId;


	public PublishWeeklyMvTasklet(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}


	/**
	 * 1. 기존 MV 해당 기간 rows 삭제
	 * 2. staging rows를 MV로 복사
	 * 3. staging 정리
	 * - 트랜잭션 롤백 시 기존 MV 유지됨
	 */
	@Override
	@Transactional
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		LocalDate target = LocalDate.parse(targetDate);
		LocalDate weekStart = target.with(DayOfWeek.MONDAY);

		// 1. 기존 MV 해당 기간 삭제
		int deleted = jdbcTemplate.update(
			"DELETE FROM mv_product_rank_weekly WHERE week_start_date = ? AND scorer_type = ?",
			Date.valueOf(weekStart), scorerType
		);

		// 2. staging → MV 복사
		int inserted = jdbcTemplate.update(
			"""
			INSERT INTO mv_product_rank_weekly (
			    week_start_date, week_end_date, scorer_type,
			    rank_position, score,
			    like_count, sales_count, view_count,
			    product_id, product_name, brand_id, brand_name, price,
			    created_at, updated_at
			)
			SELECT
			    week_start_date, week_end_date, scorer_type,
			    rank_position, score,
			    like_count, sales_count, view_count,
			    product_id, product_name, brand_id, brand_name, price,
			    created_at, updated_at
			FROM stg_product_rank_weekly
			WHERE job_execution_id = ?
			ORDER BY rank_position ASC
			""",
			jobExecutionId
		);

		// 3. staging 정리 (해당 JobExecution rows)
		jdbcTemplate.update(
			"DELETE FROM stg_product_rank_weekly WHERE job_execution_id = ?",
			jobExecutionId
		);

		log.info("[PublishWeeklyMv] week_start={}, scorerType={}, 기존MV삭제={}, MV적재={}건",
			weekStart, scorerType, deleted, inserted);

		contribution.incrementWriteCount(inserted);

		return RepeatStatus.FINISHED;
	}

}
