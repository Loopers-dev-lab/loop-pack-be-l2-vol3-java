package com.loopers.application.ranking;

public record RankingResult(
    int rank,
    Long productId,
    String productName,
    Long productPrice,
    String productImageUrl,
    Double score
) {
    public static RankingResult of(int rank, Long productId, String productName, Long productPrice, String productImageUrl, Double score) {
        return new RankingResult(rank, productId, productName, productPrice, productImageUrl, score);
    }
}
