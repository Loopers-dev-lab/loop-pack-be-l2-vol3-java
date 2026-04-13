package com.loopers.application.metrics;

import java.time.ZonedDateTime;

public record CatalogMetricEvent(
        String eventId,
        Type type,
        Long productId,
        boolean liked,
        ZonedDateTime occurredAt
) {
    public enum Type { VIEWED, LIKED }
}
