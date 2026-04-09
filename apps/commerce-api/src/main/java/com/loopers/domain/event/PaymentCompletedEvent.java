package com.loopers.domain.event;


import com.loopers.domain.order.OrderItemSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PaymentCompletedEvent(
        Long paymentId,
        Long orderId,
        Long userId,
        BigDecimal amount,
        List<OrderItemSnapshot> items,
        Instant occurredAt
) {
}
