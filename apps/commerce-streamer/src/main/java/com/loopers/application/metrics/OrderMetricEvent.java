package com.loopers.application.metrics;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderMetricEvent(
        String eventId,
        Type type,
        List<Long> productIds,
        long totalAmount,
        ZonedDateTime occurredAt
) {
    public enum Type { CREATED, CANCELED }
}
