package com.loopers.batch.job.productranking.weekly.dto;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


/**
 * 주간 랭킹 Staging 테이블 INSERT 행 DTO
 * - Chunk Writer가 stg_product_rank_weekly에 적재
 * - beanMapped()로 필드명 → 컬럼명 자동 매핑
 */
public record StagingWeeklyProductRankRow(
	Long jobExecutionId,
	LocalDate weekStartDate,
	LocalDate weekEndDate,
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
	 * Reader 집계 결과를 staging row로 변환한다.
	 * - createdAt/updatedAt은 현재 시각으로 초기화
	 */
	public static StagingWeeklyProductRankRow from(
		ProductRankAggregate aggregate,
		LocalDate weekStartDate,
		LocalDate weekEndDate,
		String scorerType,
		Long jobExecutionId
	) {
		LocalDateTime now = LocalDateTime.now();
		return new StagingWeeklyProductRankRow(
			jobExecutionId,
			weekStartDate,
			weekEndDate,
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
