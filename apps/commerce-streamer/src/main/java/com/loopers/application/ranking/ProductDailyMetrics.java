package com.loopers.application.ranking;

public record ProductDailyMetrics(
        String productId,
        long likeCount,
        long salesCount,
        long viewCount
) {
}
