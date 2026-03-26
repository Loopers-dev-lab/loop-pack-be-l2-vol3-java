package com.loopers.domain.order.event;

/**
 * 주문 만료 이벤트.
 */
public record OrderExpiredEvent(
        Long orderId,
        Long userId
) {
}
