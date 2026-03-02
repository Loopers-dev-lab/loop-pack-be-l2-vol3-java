package com.loopers.application.productlike;

import com.loopers.domain.productlike.ProductLike;

import java.time.ZonedDateTime;

public record ProductLikeInfo(
        Long id,
        Long userId,
        Long productId,
        ZonedDateTime createdAt
) {
    public static ProductLikeInfo from(ProductLike productLike) {
        return new ProductLikeInfo(
                productLike.getId(),
                productLike.getUserId(),
                productLike.getProductId(),
                productLike.getCreatedAt()
        );
    }
}
