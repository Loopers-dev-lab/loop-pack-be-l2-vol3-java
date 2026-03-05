package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;

import java.time.ZonedDateTime;

public record LikeInfo(
        Long id,
        Long userId,
        Long productId,
        String productName,
        Long brandId,
        String brandName,
        int price,
        boolean inStock,
        int likeCount,
        ZonedDateTime createdAt
) {
    public static LikeInfo of(Like like, Product product, String brandName) {
        return new LikeInfo(
                like.getId(),
                like.getUserId(),
                product.getId(),
                product.getName(),
                product.getBrandId(),
                brandName,
                product.getPrice().getAmount(),
                product.getStock().getQuantity() > 0,
                product.getLikeCount(),
                like.getCreatedAt()
        );
    }
}
