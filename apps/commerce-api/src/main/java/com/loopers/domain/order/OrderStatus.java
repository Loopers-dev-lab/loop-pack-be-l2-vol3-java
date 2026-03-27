package com.loopers.domain.order;

public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    CONFIRMED,
    SHIPPING,
    DELIVERED,
    CANCELLED
}
