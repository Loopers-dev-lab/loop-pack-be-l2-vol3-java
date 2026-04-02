package com.loopers.application.event;

import java.time.ZonedDateTime;

/**
 * 주문 완료 이벤트.
 * 주문이 성공적으로 생성되었을 때 발행됨.
 */
public record OrderCompletedEvent(
    Long orderId,
    Long userId,
    Long productId,
    Integer quantity,
    Long totalAmount,
    ZonedDateTime occurredAt
) {
    public static OrderCompletedEvent of(Long orderId, Long userId, Long productId, Integer quantity, Long totalAmount) {
        return new OrderCompletedEvent(orderId, userId, productId, quantity, totalAmount, ZonedDateTime.now());
    }
}
