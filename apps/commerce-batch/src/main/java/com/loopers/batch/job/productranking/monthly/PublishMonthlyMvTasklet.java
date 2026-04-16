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
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;


/**
 * 월간 랭킹 MV Publish Tasklet (Step 3)
 * - 단일 트랜잭션: MV DELETE → staging INSERT → staging 정리
 */

@StepScope
@Component
@Slf4j
public class PublishMonthlyMvTasklet implements Tasklet {

	// jdbc
	private final JdbcTemplate jdbcTemplate;

	@Value("#{jobParameters['targetDate']}")
	private String targetDate;

	@Value("#{jobParameters['scorerType'] ?: 'SATURATION'}")
	private String scorerType;

	@Value("#{stepExecution.jobExecution.id}")
	private Long jobExecutionId;


	public PublishMonthlyMvTasklet(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}


	@Override
	@Transactional
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		LocalDate target = LocalDate.parse(targetDate);
		LocalDate monthStart = target.withDayOfMonth(1);

		// 1. 기존 MV 해당 기간 삭제
		int deleted = jdbcTemplate.update(
			"DELETE FROM mv_product_rank_monthly WHERE month_start_date = ? AND scorer_type = ?",
			Date.valueOf(monthStart), scorerType
		);

		// 2. staging → MV 복사
		int inserted = jdbcTemplate.update(
			"""
			INSERT INTO mv_product_rank_monthly (
			    month_start_date, month_key, scorer_type,
			    rank_position, score,
			    like_count, sales_count, view_count,
			    product_id, product_name, brand_id, brand_name, price,
			    created_at, updated_at
			)
			SELECT
			    month_start_date, month_key, scorer_type,
			    rank_position, score,
			    like_count, sales_count, view_count,
			    product_id, product_name, brand_id, brand_name, price,
			    created_at, updated_at
			FROM stg_product_rank_monthly
			WHERE job_execution_id = ?
			ORDER BY rank_position ASC
			""",
			jobExecutionId
		);

		// 3. staging 정리
		jdbcTemplate.update(
			"DELETE FROM stg_product_rank_monthly WHERE job_execution_id = ?",
			jobExecutionId
		);

		log.info("[PublishMonthlyMv] month_start={}, scorerType={}, 기존MV삭제={}, MV적재={}건",
			monthStart, scorerType, deleted, inserted);

		contribution.incrementWriteCount(inserted);

		return RepeatStatus.FINISHED;
	}

}
