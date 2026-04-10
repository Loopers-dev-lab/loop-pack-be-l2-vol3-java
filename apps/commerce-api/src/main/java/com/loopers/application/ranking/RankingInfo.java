package com.loopers.application.ranking;

public record RankingInfo(
    Long productId,
    double score,
    long rank
) {}
