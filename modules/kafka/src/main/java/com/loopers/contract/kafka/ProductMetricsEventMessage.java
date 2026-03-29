package com.loopers.contract.kafka;

import java.time.Instant;
import java.util.UUID;

public record ProductMetricsEventMessage(
        UUID eventId,
        String actionType,
        String productId,
        long deltaLike,
        long deltaSales,
        long deltaView,
        long version,
        Instant updatedAt
) {
}
