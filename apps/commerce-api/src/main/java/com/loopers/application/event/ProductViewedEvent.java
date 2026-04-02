package com.loopers.application.event;

import java.time.ZonedDateTime;

/**
 * 상품 조회 이벤트.
 * 사용자가 상품 상세 페이지를 조회했을 때 발행됨.
 */
public record ProductViewedEvent(
    Long userId,
    Long productId,
    ZonedDateTime occurredAt
) {
    public static ProductViewedEvent of(Long userId, Long productId) {
        return new ProductViewedEvent(userId, productId, ZonedDateTime.now());
    }
}
