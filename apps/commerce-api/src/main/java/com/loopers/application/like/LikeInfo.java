package com.loopers.application.like;

import com.loopers.domain.like.Like;

import java.time.ZonedDateTime;

public record LikeInfo(
        Long id,
        Long userId,
        Long productId,
        ZonedDateTime createdAt
) {
    public static LikeInfo from(Like like) {
        return new LikeInfo(
                like.getId(),
                like.getUserId(),
                like.getProductId(),
                like.getCreatedAt()
        );
    }
}
