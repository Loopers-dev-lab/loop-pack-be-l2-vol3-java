package com.loopers.domain.event;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderCanceledEvent(
        Long orderId,
        Long userId,
        List<Long> productIds,
        long totalAmount,
        ZonedDateTime occurredAt
) {
}
