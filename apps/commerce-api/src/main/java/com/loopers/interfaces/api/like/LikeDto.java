package com.loopers.interfaces.api.like;

import com.loopers.domain.like.Like;

public class LikeDto {

    public record LikeResponse(
        Long id,
        Long memberId,
        Long productId
    ) {
        public static LikeResponse from(Like like) {
            return new LikeResponse(like.getId(), like.getMemberId(), like.getProductId());
        }
    }
}
