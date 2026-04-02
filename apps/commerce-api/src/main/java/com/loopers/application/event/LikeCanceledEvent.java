package com.loopers.application.event;

import java.time.ZonedDateTime;

/**
 * 좋아요 취소 이벤트.
 * 사용자가 상품 좋아요를 취소했을 때 발행됨.
 */
public record LikeCanceledEvent(
    Long likeId,
    Long userId,
    Long productId,
    ZonedDateTime occurredAt
) {
    public static LikeCanceledEvent of(Long likeId, Long userId, Long productId) {
        return new LikeCanceledEvent(likeId, userId, productId, ZonedDateTime.now());
    }
}
