package com.loopers.batch.job.ranking;

public record ScoredProductMetrics(
    Long productId,
    double score,
    long viewCount,
    long likeCount,
    long salesQuantity
) {
}
