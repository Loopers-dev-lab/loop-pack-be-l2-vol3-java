package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeWithProduct;

import java.time.ZonedDateTime;

public class LikeV1Dto {

    public record LikeResponse(
        Long likeId,
        Long productId,
        String productName,
        int productPrice,
        ZonedDateTime likedAt
    ) {
        public static LikeResponse from(LikeWithProduct lwp) {
            return new LikeResponse(
                lwp.likeId(),
                lwp.productId(),
                lwp.productName(),
                lwp.productPrice(),
                lwp.likedAt()
            );
        }
    }
}
