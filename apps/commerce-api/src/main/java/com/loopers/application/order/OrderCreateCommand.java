package com.loopers.application.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Objects;

@Getter
public class OrderCreateCommand {
    private final Long userId;
    private final List<OrderItemCommand> items;
    private final Long couponId;

    public OrderCreateCommand(Long userId, List<OrderItemCommand> items, Long couponId) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        this.couponId = couponId;
    }

    public OrderCreateCommand(Long userId, List<OrderItemCommand> items) {
        this(userId, items, null);
    }

    @Getter
    @RequiredArgsConstructor
    public static class OrderItemCommand {
        private final Long optionId;
        private final int quantity;
    }
}
