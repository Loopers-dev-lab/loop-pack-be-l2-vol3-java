package com.loopers.application.ranking;

import com.loopers.domain.product.Product;

import java.math.BigDecimal;

public record RankingInfo(
        long rank,
        Long productId,
        double score,
        String name,
        BigDecimal price,
        String imageUrl,
        Long brandId,
        String brandName
) {
    public static RankingInfo of(long rank, double score, Product product, String brandName) {
        return new RankingInfo(
                rank,
                product.getId(),
                score,
                product.getName(),
                product.getPrice(),
                product.getImageUrl(),
                product.getBrandId(),
                brandName
        );
    }
}
