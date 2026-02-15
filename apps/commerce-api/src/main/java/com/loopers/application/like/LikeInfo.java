package com.loopers.application.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;

import java.time.ZonedDateTime;

public record LikeInfo(
    Long likeId,
    Long productId,
    String productName,
    String brandName,
    int price,
    int likeCount,
    ZonedDateTime likedAt
) {
    public static LikeInfo from(Like like, Product product, Brand brand) {
        return new LikeInfo(
            like.getId(),
            product.getId(),
            product.getName(),
            brand.getName(),
            product.getPrice().amount(),
            product.getLikeCount(),
            like.getCreatedAt()
        );
    }
}
