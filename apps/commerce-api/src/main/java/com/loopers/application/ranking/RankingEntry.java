package com.loopers.application.ranking;

public record RankingEntry(
        int rank,
        Long productId,
        double score
) {
}
