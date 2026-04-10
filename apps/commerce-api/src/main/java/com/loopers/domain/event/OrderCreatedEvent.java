package com.loopers.domain.event;

import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        Long memberId,
        int totalPrice,
        List<OrderProductInfo> orderProducts
) {
    public record OrderProductInfo(Long productId, int price, int quantity) {
    }
}
