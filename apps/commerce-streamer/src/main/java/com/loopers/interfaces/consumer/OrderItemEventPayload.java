package com.loopers.interfaces.consumer;

import java.math.BigDecimal;

public record OrderItemEventPayload(
        Long productDbId,
        BigDecimal price,
        int quantity
) {
}
