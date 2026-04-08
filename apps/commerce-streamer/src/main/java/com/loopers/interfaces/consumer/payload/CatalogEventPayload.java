package com.loopers.interfaces.consumer.payload;

import java.time.ZonedDateTime;

public record CatalogEventPayload(
        String eventId,
        String eventType,
        Long userId,
        Long productId,
        int delta,
        ZonedDateTime occurredAt
) {}
