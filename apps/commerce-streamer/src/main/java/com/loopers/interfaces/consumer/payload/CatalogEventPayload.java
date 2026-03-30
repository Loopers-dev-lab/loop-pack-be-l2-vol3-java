package com.loopers.interfaces.consumer.payload;

public record CatalogEventPayload(
        String eventId,
        String eventType,
        Long userId,
        Long productId,
        int delta
) {}
