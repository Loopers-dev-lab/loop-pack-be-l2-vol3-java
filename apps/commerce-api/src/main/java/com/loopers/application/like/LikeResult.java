package com.loopers.application.like;

import com.loopers.domain.like.Like;

import java.time.ZonedDateTime;

/**
 * 좋아요 응답 DTO.
 */
public record LikeResult(
    Long id,
    Long userId,
    Long productId,
    ZonedDateTime createdAt
) {
    public static LikeResult from(Like like) {
        return new LikeResult(
            like.getId(),
            like.getUserId(),
            like.getProductId(),
            like.getCreatedAt()
        );
    }
}
