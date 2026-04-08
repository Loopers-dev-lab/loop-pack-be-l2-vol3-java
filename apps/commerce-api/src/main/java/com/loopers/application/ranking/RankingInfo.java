package com.loopers.application.ranking;

public record RankingInfo(
    int rank,
    Long productId,
    String productName,
    String brandName,
    long price,
    double score
) {}
