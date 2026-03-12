package com.loopers.application.order.command;

import java.util.List;
import java.util.UUID;

public record CreateOrderCommand(
        String memberId,
        List<OrderItemCommand> items,
        UUID couponId
) {
    public CreateOrderCommand(String memberId, List<OrderItemCommand> items) {
        this(memberId, items, null);
    }

    public record OrderItemCommand(UUID productId, int quantity) {}
}
