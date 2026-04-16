package com.loopers.batch.job.productranking.weekly.dto;


import java.math.BigDecimal;


/**
 * Reader SQL 결과 집계 DTO — 주간/월간 공용
 * - product_metrics BETWEEN ? AND ? GROUP BY product_id 결과
 * - SQL에서 score + ROW_NUMBER() 계산 완료 후 반환
 */
public record ProductRankAggregate(
	Long productId,
	Long viewCount,
	Long likeCount,
	Long salesCount,
	String productName,
	Long brandId,
	String brandName,
	BigDecimal price,
	BigDecimal score,
	int rankPosition
) {
}
