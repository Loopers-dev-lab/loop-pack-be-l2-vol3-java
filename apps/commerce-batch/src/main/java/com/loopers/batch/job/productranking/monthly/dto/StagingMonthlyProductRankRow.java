package com.loopers.batch.job.productranking.monthly.dto;


import com.loopers.batch.job.productranking.weekly.dto.ProductRankAggregate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


/**
 * 월간 랭킹 Staging 테이블 INSERT 행 DTO
 * - Chunk Writer가 stg_product_rank_monthly에 적재
 * - beanMapped()로 필드명 → 컬럼명 자동 매핑
 */
public record StagingMonthlyProductRankRow(
	Long jobExecutionId,
	LocalDate monthStartDate,
	String monthKey,
	String scorerType,
	int rankPosition,
	BigDecimal score,
	Long likeCount,
	Long salesCount,
	Long viewCount,
	Long productId,
	String productName,
	Long brandId,
	String brandName,
	BigDecimal price,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {

	/**
	 * Reader 집계 결과를 monthly staging row로 변환한다.
	 */
	public static StagingMonthlyProductRankRow from(
		ProductRankAggregate aggregate,
		LocalDate monthStartDate,
		String monthKey,
		String scorerType,
		Long jobExecutionId
	) {
		LocalDateTime now = LocalDateTime.now();
		return new StagingMonthlyProductRankRow(
			jobExecutionId,
			monthStartDate,
			monthKey,
			scorerType,
			aggregate.rankPosition(),
			aggregate.score(),
			aggregate.likeCount(),
			aggregate.salesCount(),
			aggregate.viewCount(),
			aggregate.productId(),
			aggregate.productName(),
			aggregate.brandId(),
			aggregate.brandName(),
			aggregate.price(),
			now,
			now
		);
	}

}
