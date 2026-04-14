package com.loopers.interfaces.consumer.payload;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderCreatedEventPayload(
        String eventId,
        String eventType,
        Long userId,
        String orderId,
        Long totalAmount,
        List<Item> items,
        ZonedDateTime occurredAt
) {
    public record Item(Long productId, Integer quantity, Integer unitPrice) {}
}
