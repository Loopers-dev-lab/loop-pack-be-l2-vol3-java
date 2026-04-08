package com.loopers.application.event;

import java.time.Instant;

public record ProductViewedEvent(
        String viewerId,
        Long productId,
        Instant occurredAt
) {
}
