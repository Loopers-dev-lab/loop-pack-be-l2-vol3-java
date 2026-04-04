package com.loopers.domain.ranking;

public record ProductRanking(
    Long productId,
    double score,
    long rank
) {
}
