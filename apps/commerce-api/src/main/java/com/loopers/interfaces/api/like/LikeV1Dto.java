package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;
import com.loopers.application.like.LikedProductInfo;

import java.time.ZonedDateTime;

public class LikeV1Dto {

    public record LikeResponse(
            Long id,
            Long userId,
            Long productId,
            ZonedDateTime createdAt
    ) {
        public static LikeResponse from(LikeInfo info) {
            return new LikeResponse(
                    info.id(),
                    info.userId(),
                    info.productId(),
                    info.createdAt()
            );
        }
    }

    public record LikedProductResponse(
            Long likeId,
            Long productId,
            String productName,
            Integer price,
            ZonedDateTime likedAt
    ) {
        public static LikedProductResponse from(LikedProductInfo info) {
            return new LikedProductResponse(
                    info.likeId(),
                    info.product().id(),
                    info.product().name(),
                    info.product().price(),
                    info.likedAt()
            );
        }
    }
}
