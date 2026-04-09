package com.loopers.application.ranking.cache;

import com.loopers.domain.product.Product;

import java.util.UUID;

public record RankingProductCacheItem(
        UUID productId,
        String name,
        Integer price,
        UUID brandId,
        Integer likeCount
) {
    public static RankingProductCacheItem from(Product product) {
        return new RankingProductCacheItem(
                product.id(),
                product.name(),
                product.price(),
                product.brandId(),
                product.likeCount()
        );
    }
}
