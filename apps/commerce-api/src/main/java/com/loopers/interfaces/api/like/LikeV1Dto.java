package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeInfo;

public class LikeV1Dto {

    public record LikeResponse(Long likeId, Long userId, Long productId) {

        public static LikeResponse from(LikeInfo info) {
            return new LikeResponse(
                info.likeId(),
                info.userId(),
                info.productId()
            );
        }
    }
}
