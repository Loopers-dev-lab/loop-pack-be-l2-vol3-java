package com.loopers.batch.domain.ranking;

public record AggregatedRankingRow(
        int rankNo,
        Long productId,
        long viewCount,
        long likeCount,
        long orderCount,
        double score
) {
}
