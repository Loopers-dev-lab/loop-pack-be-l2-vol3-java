package com.loopers.batch.job.productranking.weekly;


import com.loopers.batch.job.productranking.weekly.dto.StagingWeeklyProductRankRow;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;


/**
 * 주간 랭킹 Writer 설정
 * - stg_product_rank_weekly에 INSERT (ON DUPLICATE KEY UPDATE)
 * - PK: (job_execution_id, rank_position) — 동일 JobExecution 재실행 시 upsert
 */

@Configuration
public class WeeklyProductRankingWriterConfig {

	@Bean("weeklyProductRankingWriter")
	public JdbcBatchItemWriter<StagingWeeklyProductRankRow> weeklyProductRankingWriter(DataSource dataSource) {
		return new JdbcBatchItemWriterBuilder<StagingWeeklyProductRankRow>()
			.dataSource(dataSource)
			.sql("""
				INSERT INTO stg_product_rank_weekly (
				    job_execution_id,
				    week_start_date, week_end_date, scorer_type,
				    rank_position, score,
				    like_count, sales_count, view_count,
				    product_id, product_name, brand_id, brand_name, price,
				    created_at, updated_at
				) VALUES (
				    :jobExecutionId,
				    :weekStartDate, :weekEndDate, :scorerType,
				    :rankPosition, :score,
				    :likeCount, :salesCount, :viewCount,
				    :productId, :productName, :brandId, :brandName, :price,
				    :createdAt, :updatedAt
				)
				ON DUPLICATE KEY UPDATE
				    week_start_date = VALUES(week_start_date),
				    week_end_date   = VALUES(week_end_date),
				    scorer_type     = VALUES(scorer_type),
				    score           = VALUES(score),
				    like_count      = VALUES(like_count),
				    sales_count     = VALUES(sales_count),
				    view_count      = VALUES(view_count),
				    product_id      = VALUES(product_id),
				    product_name    = VALUES(product_name),
				    brand_id        = VALUES(brand_id),
				    brand_name      = VALUES(brand_name),
				    price           = VALUES(price),
				    updated_at      = VALUES(updated_at)
				""")
			.beanMapped()
			.build();
	}

}
