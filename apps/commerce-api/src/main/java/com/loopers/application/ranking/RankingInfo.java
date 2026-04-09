package com.loopers.application.ranking;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.SellingStatus;

public record RankingInfo(
    int rank,
    Long productId,
    String name,
    String description,
    int price,
    SellingStatus sellingStatus,
    double score
) {
    public static RankingInfo of(int rank, Product product, double score) {
        return new RankingInfo(
            rank,
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getSellingStatus(),
            score
        );
    }
}
