package com.loopers.application.payment.event;

import com.loopers.application.order.event.OrderItemSnapshot;

import java.time.ZonedDateTime;
import java.util.List;

public record PaymentCompletedEvent(
    Long paymentId,
    Long orderId,
    Long userId,
    int amount,
    List<OrderItemSnapshot> items,
    ZonedDateTime occurredAt
) {
    public PaymentCompletedEvent {
        items = List.copyOf(items);
    }
}
