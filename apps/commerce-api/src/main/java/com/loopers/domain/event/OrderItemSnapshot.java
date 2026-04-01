package com.loopers.domain.event;

public record OrderItemSnapshot(long productId, int quantity, int price) {
}
