package com.loopers.domain.event;

public record OrderItemPayload(
    Long productId,
    int quantity,
    int price
) {
}
