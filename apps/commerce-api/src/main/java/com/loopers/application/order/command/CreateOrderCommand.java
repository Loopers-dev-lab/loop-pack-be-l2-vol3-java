package com.loopers.application.order.command;

import java.util.List;

public record CreateOrderCommand(
        Long userId,
        List<OrderItemCommand> items
) {
    public record OrderItemCommand(Long productId, int quantity) {}
}
