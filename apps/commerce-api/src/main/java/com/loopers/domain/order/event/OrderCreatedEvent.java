package com.loopers.domain.order.event;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;

import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        Long memberId,
        int totalAmount,
        int itemCount
) {
    public static OrderCreatedEvent from(OrderModel order, List<OrderItemModel> items) {
        return new OrderCreatedEvent(
                order.getId(),
                order.getMemberId(),
                order.getTotalAmount(),
                items.size()
        );
    }
}
