package com.loopers.application.order;

import java.math.BigDecimal;

public record OrderItemPayload(
        Long productDbId,
        BigDecimal price,
        int quantity
) {
}
