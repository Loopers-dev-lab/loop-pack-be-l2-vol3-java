package com.loopers.domain.product.event;

import java.time.ZonedDateTime;

public record ProductViewedEvent(
        Long productId,
        Long userId,
        ZonedDateTime viewedAt
) {}
