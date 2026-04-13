package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;

public record RankingProductInfo(
        Long productId,
        String productName,
        Integer price,
        String brandName,
        Long rank,
        Double score
) {
    public static RankingProductInfo of(ProductInfo product, long rank, double score) {
        return new RankingProductInfo(
                product.id(),
                product.name(),
                product.price(),
                product.brand().name(),
                rank,
                score
        );
    }
}
