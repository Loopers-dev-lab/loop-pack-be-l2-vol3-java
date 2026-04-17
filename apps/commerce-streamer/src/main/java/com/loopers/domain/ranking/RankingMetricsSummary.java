package com.loopers.domain.ranking;

import java.math.BigDecimal;

/**
 * 특정 상품의 일간 집계 합산 결과.
 *
 * SyncScheduler가 SUM × weight 계산에 사용한다.
 */
public record RankingMetricsSummary(
        Long productId,
        long totalViewCount,
        long totalLikeCount,
        BigDecimal totalOrderRevenue
) {
}
