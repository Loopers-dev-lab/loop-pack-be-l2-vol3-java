package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;

public record RankingInfo(
    Long rank,
    Long productId,
    String name,
    String brandName,
    Integer price,
    Integer stock,
    Integer likesCount,
    String description,
    String imageUrl
) {
    public static RankingInfo of(long rank, ProductInfo productInfo) {
        return new RankingInfo(
            rank,
            productInfo.id(),
            productInfo.name(),
            productInfo.brandName(),
            productInfo.price(),
            productInfo.stock(),
            productInfo.likesCount(),
            productInfo.description(),
            productInfo.imageUrl()
        );
    }
}
