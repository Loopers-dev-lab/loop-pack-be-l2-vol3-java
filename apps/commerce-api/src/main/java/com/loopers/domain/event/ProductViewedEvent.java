package com.loopers.domain.event;

import java.time.Instant;

public record ProductViewedEvent(
        String viewerId,
        Long productId,
        Instant occurredAt
) {
}
