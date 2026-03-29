package com.loopers.application.order.event;

import com.loopers.domain.order.Order;

import java.util.List;

public record OrderCancelledEvent(
    Long orderId,
    Long userId,
    List<CancelledItem> items
) {

    public record CancelledItem(
        Long productId,
        int quantity
    ) {
    }

    public static OrderCancelledEvent from(Order order) {
        List<CancelledItem> items = order.getOrderItems().stream()
            .map(item -> new CancelledItem(item.getProductId(), item.getQuantity()))
            .toList();

        return new OrderCancelledEvent(order.getId(), order.getUserId(), items);
    }
}
