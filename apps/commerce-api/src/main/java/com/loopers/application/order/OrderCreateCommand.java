package com.loopers.application.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class OrderCreateCommand {
    private final Long userId;
    private final List<OrderItemCommand> items;

    @Getter
    @RequiredArgsConstructor
    public static class OrderItemCommand {
        private final Long productId;
        private final int quantity;
    }
}
