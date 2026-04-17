package com.loopers.domain.ranking;

public record ProductMetricsAggregation(
    Long productId,
    int totalViewCount,
    int totalLikeCount,
    int totalSaleCount,
    double score
) {}
