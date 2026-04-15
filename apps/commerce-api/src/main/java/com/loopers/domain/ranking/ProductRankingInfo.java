package com.loopers.domain.ranking;

public record ProductRankingInfo(
    Long productId,
    Double score,
    Long rank
) {
}
