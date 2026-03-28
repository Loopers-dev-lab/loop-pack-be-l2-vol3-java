package com.loopers.application.product.event;

import java.time.ZonedDateTime;

public record ProductViewedEvent(
    Long productId,
    Long userId,
    ZonedDateTime occurredAt
) {
}
