package com.loopers.application.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.product.ProductModel;

import java.time.ZonedDateTime;

public record LikeInfo(
    Long likeId,
    Long productId,
    String productName,
    int productPrice,
    String brandName,
    ZonedDateTime likedAt
) {
    public static LikeInfo from(LikeModel like, ProductModel product, BrandModel brand) {
        return new LikeInfo(
            like.getId(),
            product.getId(),
            product.getName(),
            product.getPrice().value(),
            brand.getName(),
            like.getCreatedAt()
        );
    }
}
