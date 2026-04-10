package com.loopers.application.ranking;

public record RankingWithProduct(
    long rank,
    double score,
    Long productId,
    String productName,
    int productPrice,
    String brandName
) {}
