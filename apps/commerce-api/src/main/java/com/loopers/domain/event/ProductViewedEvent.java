package com.loopers.domain.event;

import java.time.ZonedDateTime;

public record ProductViewedEvent(
        Long productId,
        Long userId,
        ZonedDateTime occurredAt
) {
}
