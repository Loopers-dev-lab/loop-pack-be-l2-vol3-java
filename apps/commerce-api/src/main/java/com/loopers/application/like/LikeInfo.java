package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;

import java.time.ZonedDateTime;

/**
 * 좋아요 응답용 애플리케이션 DTO.
 * Controller 응답에 사용하며, interfaces DTO와 분리한다.
 */
public record LikeInfo(
    Long id,
    Long userId,
    Long productId,
    ZonedDateTime createdAt
) {
    public static LikeInfo from(LikeModel like) {
        if (like == null) {
            return null;
        }
        return new LikeInfo(
            like.getId(),
            like.getUserId(),
            like.getProductId(),
            like.getCreatedAt()
        );
    }
}
