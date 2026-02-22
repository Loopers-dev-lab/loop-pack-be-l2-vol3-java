package com.loopers.domain.order;

public enum OrderStatus {
    PENDING,
    PAID,
    PREPARING,
    SHIPPED,
    DELIVERED,
    CANCELED;

    public boolean canCancel() {
        return this == PENDING || this == PAID;
    }

    public boolean canShip() {
        return this == PREPARING;
    }

    public boolean canDeliver() {
        return this == SHIPPED;
    }
}
