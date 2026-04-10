package com.loopers.application.ranking;

public record ProductMetricsSummary(
        String productId,
        long likeCount,
        long salesCount,
        long salesAmount,
        long viewCount
) {
}
