package com.loopers.application.order.event;

import java.time.ZonedDateTime;

public record OrderCancelledEvent(
    Long orderId,
    Long userId,
    ZonedDateTime occurredAt
) {
}
