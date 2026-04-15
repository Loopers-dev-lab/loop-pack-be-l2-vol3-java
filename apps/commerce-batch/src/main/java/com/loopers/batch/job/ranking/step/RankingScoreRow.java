package com.loopers.batch.job.ranking.step;

public record RankingScoreRow(
        Long productId,
        long viewCount,
        long likeCount,
        long orderAmount,
        double score,
        int ranking
) {
}
