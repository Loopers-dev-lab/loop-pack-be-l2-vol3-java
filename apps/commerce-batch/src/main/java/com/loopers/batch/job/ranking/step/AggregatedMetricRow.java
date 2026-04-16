package com.loopers.batch.job.ranking.step;

public record AggregatedMetricRow(
        Long productId,
        long viewCount,
        long likeCount,
        long orderAmount
) {
}
