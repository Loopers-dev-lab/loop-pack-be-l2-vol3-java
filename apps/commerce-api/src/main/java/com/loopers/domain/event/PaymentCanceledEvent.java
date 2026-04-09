package com.loopers.domain.event;


import java.math.BigDecimal;
import java.util.List;

public record PaymentCanceledEvent(
        Long paymentId,
        Long orderId,
        Long userId,
        List<OrderItem> items
) {

    public

    record OrderItem (Long productId, Integer quantity, BigDecimal unitPrice) {
        public static OrderItem of (Long productId, Integer quantity, BigDecimal unitPrice) {
            return new OrderItem(productId, quantity, unitPrice);
        }
    }
}
