package com.loopers.application.order.command;

import com.loopers.domain.payment.CardType;

import java.util.List;
import java.util.UUID;

public record CreateOrderCommand(
        String memberId,
        List<OrderItemCommand> items,
        UUID couponId,
        int pointAmount,
        CardType cardType,
        String cardNo
) {
    public CreateOrderCommand(String memberId, List<OrderItemCommand> items) {
        this(memberId, items, null, 0, CardType.SAMSUNG, "1234-5678-1234-5678");
    }

    public CreateOrderCommand(String memberId, List<OrderItemCommand> items, UUID couponId) {
        this(memberId, items, couponId, 0, CardType.SAMSUNG, "1234-5678-1234-5678");
    }

    public record OrderItemCommand(UUID productId, int quantity) {}
}
