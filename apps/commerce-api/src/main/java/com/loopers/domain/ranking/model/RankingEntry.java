package com.loopers.domain.ranking.model;

public record RankingEntry(
        Long productId,
        double score,
        long rank
) {
}
