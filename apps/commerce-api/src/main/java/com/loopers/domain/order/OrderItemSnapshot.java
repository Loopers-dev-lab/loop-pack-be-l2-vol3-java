package com.loopers.domain.order;

import java.math.BigDecimal;

public record OrderItemSnapshot(
        Long productId,
        Integer quantity,
        BigDecimal unitPrice
) {
}
