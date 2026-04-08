package com.loopers.application.event;

import java.math.BigDecimal;

public record OrderItemSnapshot(
        Long productId,
        Integer quantity,
        BigDecimal unitPrice
) {
}
