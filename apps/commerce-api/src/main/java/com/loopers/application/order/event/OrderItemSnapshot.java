package com.loopers.application.order.event;

public record OrderItemSnapshot(
    Long productId,
    int quantity
) {
}
