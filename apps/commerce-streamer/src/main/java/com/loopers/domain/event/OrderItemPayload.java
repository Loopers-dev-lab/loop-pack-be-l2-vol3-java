package com.loopers.domain.event;

import org.springframework.util.Assert;

import java.util.Objects;

public record OrderItemPayload(
    Long productId,
    int quantity,
    int price
) {
    public OrderItemPayload {
        Objects.requireNonNull(productId, "productId must not be null");
        Assert.state(quantity > 0, "quantity must be greater than 0");
        Assert.state(price >= 0, "price must be greater than or equal to 0");
    }
}
