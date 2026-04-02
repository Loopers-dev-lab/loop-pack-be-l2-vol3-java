package com.loopers.domain.order;

public class OrderRequestedEvent {

    private final Long userId;

    public OrderRequestedEvent(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
