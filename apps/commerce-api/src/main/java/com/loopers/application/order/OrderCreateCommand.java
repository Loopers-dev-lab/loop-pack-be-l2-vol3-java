package com.loopers.application.order;

import com.loopers.interfaces.api.order.OrderV1Dto;

import java.util.List;

public record OrderCreateCommand(Long userId, List<OrderItemCommand> items) {
    public static OrderCreateCommand from(Long userId, OrderV1Dto.CreateRequest request) {
        List<OrderItemCommand> items = request.items().stream()
                                              .map(item -> new OrderItemCommand(item.productId(), item.quantity()))
                                              .toList();
        return new OrderCreateCommand(userId, items);
    }
}
