package com.loopers.application.product.event;

import java.time.Instant;

public record ProductViewedEvent(
    Long productId,
    Long userId,
    Instant viewedAt
) {
}
