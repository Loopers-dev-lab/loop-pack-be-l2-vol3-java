package com.loopers.application.order.command;

import java.util.List;
import java.util.UUID;

public record CreateOrderCommand(
        UUID userId,
        List<OrderItemCommand> items
) {
    public record OrderItemCommand(UUID productId, int quantity) {}
}
