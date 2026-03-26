package com.loopers.domain.order.event;

/**
 * 주문 취소 이벤트.
 */
public record OrderCancelledEvent(
        Long orderId,
        Long userId
) {
}
