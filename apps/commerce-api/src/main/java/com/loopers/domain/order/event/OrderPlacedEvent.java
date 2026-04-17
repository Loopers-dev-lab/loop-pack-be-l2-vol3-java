package com.loopers.domain.order.event;

import java.util.List;

public record OrderPlacedEvent(
    Long orderId,
    Long userId,
    Long totalAmountValue,
    List<OrderItemEvent> items
) {
    public record OrderItemEvent(Long productId, long price, int quantity) {}
}
