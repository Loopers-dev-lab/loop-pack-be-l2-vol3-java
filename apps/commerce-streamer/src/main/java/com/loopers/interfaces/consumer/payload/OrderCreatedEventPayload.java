package com.loopers.interfaces.consumer.payload;

import java.util.List;

public record OrderCreatedEventPayload(
        String eventId,
        String eventType,
        Long userId,
        String orderId,
        Long totalAmount,
        List<Item> items
) {
    public record Item(Long productId, Integer quantity) {}
}
