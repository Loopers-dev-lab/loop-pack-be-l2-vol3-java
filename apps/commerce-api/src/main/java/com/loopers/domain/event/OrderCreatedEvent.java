package com.loopers.domain.event;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        Long userId,
        List<Long> productIds,
        ZonedDateTime occurredAt
) {
}
