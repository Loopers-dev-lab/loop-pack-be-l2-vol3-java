package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class LikeDto {

    public record LikeInfo(
        Long id,
        Long userId,
        Long productId
    ) {
        public static LikeInfo from(Like like) {
            return new LikeInfo(
                like.getId(),
                like.getUserId(),
                like.getProductId()
            );
        }
    }

    public record LikeResult(
        LikeInfo like,
        boolean alreadyLiked
    ) {
    }

    public record LikedProductInfo(
        Long productId,
        Long brandId,
        String brandName,
        String productName,
        String productDescription,
        BigDecimal productPrice,
        Integer likeCount,
        ZonedDateTime likedAt
    ) {
        public static LikedProductInfo of(Like like, Product product, String brandName) {
            return new LikedProductInfo(
                product.getId(),
                product.getBrandId(),
                brandName,
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getLikeCount(),
                like.getCreatedAt()
            );
        }
    }
}
