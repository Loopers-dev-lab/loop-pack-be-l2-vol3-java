package com.loopers.application.order.event;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;

import java.util.List;

public record OrderPlacedEvent(
    Long orderId,
    Long userId,
    List<OrderedItem> items
) {

    public record OrderedItem(
        Long productId,
        int quantity
    ) {
    }

    public static OrderPlacedEvent from(Order order) {
        List<OrderedItem> items = order.getOrderItems().stream()
            .map(item -> new OrderedItem(item.getProductId(), item.getQuantity()))
            .toList();

        return new OrderPlacedEvent(order.getId(), order.getUserId(), items);
    }
}
