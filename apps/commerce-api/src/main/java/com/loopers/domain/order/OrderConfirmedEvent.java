package com.loopers.domain.order;

public class OrderConfirmedEvent {

    private final Long orderId;
    private final Long userId;

    public OrderConfirmedEvent(Long orderId, Long userId) {
        this.orderId = orderId;
        this.userId = userId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }
}
