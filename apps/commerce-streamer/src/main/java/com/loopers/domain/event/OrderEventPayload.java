package com.loopers.domain.event;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderEventPayload(
    Long paymentId,
    Long orderId,
    Long userId,
    Integer amount,
    List<OrderItemPayload> items,
    ZonedDateTime occurredAt
) {
}
