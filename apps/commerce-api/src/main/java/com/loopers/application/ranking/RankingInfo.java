package com.loopers.application.ranking;

public record RankingInfo(
    long rank,
    Long productId,
    String productName,
    Long price,
    String brandName,
    double score
) {}
