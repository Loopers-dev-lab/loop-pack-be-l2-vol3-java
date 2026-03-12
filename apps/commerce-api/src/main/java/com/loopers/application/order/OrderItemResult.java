package com.loopers.application.order;

import com.loopers.domain.order.OrderItem;

/**
 * 주문 항목 응답 DTO.
 */
public record OrderItemResult(
    Long id,
    Long productId,
    String productName,
    int quantity,
    Long priceSnapshot,
    Long subtotal
) {
    public static OrderItemResult from(OrderItem item) {
        return new OrderItemResult(
            item.getId(),
            item.getProductId(),
            item.getProductName(),
            item.getQuantity(),
            item.getPriceSnapshot().amount(),
            item.getSubtotal().amount()
        );
    }
}
