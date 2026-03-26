package com.loopers.domain.metrics;

import java.time.ZonedDateTime;

public record CatalogEventMessage(
        String eventId,
        String eventType,
        String aggregateId,
        String payload,
        ZonedDateTime occurredAt
) {
}
