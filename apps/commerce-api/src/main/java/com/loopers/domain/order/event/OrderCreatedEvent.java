package com.loopers.domain.order.event;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;

import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        Long memberId,
        int totalAmount,
        int itemCount,
        List<OrderItemSnapshot> items
) {
    public record OrderItemSnapshot(
            Long productId,
            int price,
            int quantity
    ) {}

    public static OrderCreatedEvent from(OrderModel order, List<OrderItemModel> items) {
        List<OrderItemSnapshot> snapshots = items.stream()
                .map(item -> new OrderItemSnapshot(
                        item.getProductId(),
                        item.getProductPrice(),
                        item.getQuantity()))
                .toList();

        return new OrderCreatedEvent(
                order.getId(),
                order.getMemberId(),
                order.getTotalAmount(),
                items.size(),
                snapshots
        );
    }
}
