package com.loopers.domain.event;

import java.util.List;

public record OrderCompletedEvent(Long orderId, Long userId, List<OrderItemInfo> items) {
    public record OrderItemInfo(Long productId, Integer quantity) {}
}
