package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;

public record OrderItemInfo(
    Long id,
    Long productId,
    String productName,
    Long unitPrice,
    int quantity,
    Long lineTotalAmount
) {
    public static OrderItemInfo from(OrderItemModel orderItem) {
        return new OrderItemInfo(
            orderItem.getId(),
            orderItem.getProductId(),
            orderItem.getProductName(),
            orderItem.getUnitPrice(),
            orderItem.getQuantity(),
            orderItem.getLineTotalAmount()
        );
    }
}
