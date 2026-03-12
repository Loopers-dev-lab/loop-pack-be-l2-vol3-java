package com.loopers.interfaces.api.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.product.ProductModel;

import java.time.ZonedDateTime;

public class LikeV1Dto {

    public record LikeResponse(
        Long likeId,
        Long productId,
        String productName,
        int productPrice,
        ZonedDateTime likedAt
    ) {
        public static LikeResponse from(LikeModel like, ProductModel product) {
            return new LikeResponse(
                like.getId(),
                product.getId(),
                product.name(),
                product.price().value(),
                like.getCreatedAt()
            );
        }
    }
}
