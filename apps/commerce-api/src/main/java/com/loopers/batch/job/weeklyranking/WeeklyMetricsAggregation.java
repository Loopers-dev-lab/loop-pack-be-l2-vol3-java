package com.loopers.batch.job.weeklyranking;

import java.math.BigDecimal;

/**
 * 주간 메트릭 집계 결과 DTO.
 * Reader에서 GROUP BY 쿼리 결과를 담는 용도입니다.
 */
public record WeeklyMetricsAggregation(
    Long productId,
    long totalViewCount,
    long totalLikeCount,
    long totalOrderCount,
    BigDecimal totalScore
) {
}