package com.loopers.application.product.event;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductPriceChangedEvent(
    Long productId,
    BigDecimal newPrice,
    Instant occurredAt
) {
}
