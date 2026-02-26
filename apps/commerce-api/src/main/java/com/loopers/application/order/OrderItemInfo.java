package com.loopers.application.order;

import com.loopers.domain.order.OrderItem;

public record OrderItemInfo(
        Long id,
        Long orderId,
        Long productId,
        String productName,
        Integer price,
        Integer quantity
) {
    public static OrderItemInfo from(OrderItem item) {
        return new OrderItemInfo(
                item.getId(),
                item.getOrderId(),
                item.getProductId(),
                item.getProductName(),
                item.getPrice(),
                item.getQuantity()
        );
    }
}
