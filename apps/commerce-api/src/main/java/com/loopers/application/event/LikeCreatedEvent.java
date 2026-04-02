package com.loopers.application.event;

import java.time.ZonedDateTime;

/**
 * 좋아요 생성 이벤트.
 * 사용자가 상품에 좋아요를 등록했을 때 발행됨.
 */
public record LikeCreatedEvent(
    Long likeId,
    Long userId,
    Long productId,
    ZonedDateTime occurredAt
) {
    public static LikeCreatedEvent of(Long likeId, Long userId, Long productId) {
        return new LikeCreatedEvent(likeId, userId, productId, ZonedDateTime.now());
    }
}
