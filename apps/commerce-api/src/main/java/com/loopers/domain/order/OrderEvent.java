package com.loopers.domain.order;

import java.util.List;

public class OrderEvent {

    public record Created(Long userId, String orderId, Long totalAmount, Long issuedCouponId, List<Item> items) {

        public record Item(Long productId, Integer quantity, Integer unitPrice) {}

        public static Created from(Order order) {
            List<Item> items = order.items().stream()
                    .map(i -> new Item(i.productId(), i.quantity(), i.unitPrice()))
                    .toList();
            return new Created(order.userId(), order.orderId(), order.originalAmount() - order.discountAmount(), order.issuedCouponId(), items);
        }
    }
}
