package com.loopers.domain.order;

/**
 * 주문 상태.
 * ORDERED(결제 전) → PAID → SHIPPING → DELIVERED. 취소 시 ORDERED/PAID → CANCELLED.
 */
public enum OrderStatus {
    ORDERED,
    PAID,
    SHIPPING,
    DELIVERED,
    CANCELLED
}
