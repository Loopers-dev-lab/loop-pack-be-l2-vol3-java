package com.loopers.batch.job.productranking.monthly;


import com.loopers.batch.job.productranking.monthly.dto.StagingMonthlyProductRankRow;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;


/**
 * 월간 랭킹 Writer 설정
 * - stg_product_rank_monthly에 INSERT (ON DUPLICATE KEY UPDATE)
 */

@Configuration
public class MonthlyProductRankingWriterConfig {

	@Bean("monthlyProductRankingWriter")
	public JdbcBatchItemWriter<StagingMonthlyProductRankRow> monthlyProductRankingWriter(DataSource dataSource) {
		return new JdbcBatchItemWriterBuilder<StagingMonthlyProductRankRow>()
			.dataSource(dataSource)
			.sql("""
				INSERT INTO stg_product_rank_monthly (
				    job_execution_id,
				    month_start_date, month_key, scorer_type,
				    rank_position, score,
				    like_count, sales_count, view_count,
				    product_id, product_name, brand_id, brand_name, price,
				    created_at, updated_at
				) VALUES (
				    :jobExecutionId,
				    :monthStartDate, :monthKey, :scorerType,
				    :rankPosition, :score,
				    :likeCount, :salesCount, :viewCount,
				    :productId, :productName, :brandId, :brandName, :price,
				    :createdAt, :updatedAt
				)
				ON DUPLICATE KEY UPDATE
				    month_start_date = VALUES(month_start_date),
				    month_key        = VALUES(month_key),
				    scorer_type      = VALUES(scorer_type),
				    score            = VALUES(score),
				    like_count       = VALUES(like_count),
				    sales_count      = VALUES(sales_count),
				    view_count       = VALUES(view_count),
				    product_id       = VALUES(product_id),
				    product_name     = VALUES(product_name),
				    brand_id         = VALUES(brand_id),
				    brand_name       = VALUES(brand_name),
				    price            = VALUES(price),
				    updated_at       = VALUES(updated_at)
				""")
			.beanMapped()
			.build();
	}

}
