package com.loopers.application.order.event;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderCreatedEvent(
    Long orderId,
    Long userId,
    List<OrderItemSnapshot> items,
    ZonedDateTime occurredAt
) {
    public OrderCreatedEvent {
        items = List.copyOf(items);
    }
}
