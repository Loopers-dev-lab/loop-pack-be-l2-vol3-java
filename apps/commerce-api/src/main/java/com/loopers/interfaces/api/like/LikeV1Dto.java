package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;

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
}
