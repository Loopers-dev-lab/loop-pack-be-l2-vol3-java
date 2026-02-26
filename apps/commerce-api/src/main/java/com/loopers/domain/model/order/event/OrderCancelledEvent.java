package com.loopers.domain.model.order.event;

import com.loopers.domain.model.common.DomainEvent;

import java.time.LocalDateTime;
import java.util.List;

public record OrderCancelledEvent(
        Long orderId,
        List<CancelledItem> cancelledItems,
        LocalDateTime occurredAt
) implements DomainEvent {

    public record CancelledItem(Long productId, int quantity) {}

    public OrderCancelledEvent(Long orderId, List<CancelledItem> cancelledItems) {
        this(orderId, cancelledItems, LocalDateTime.now());
    }
}
