package com.loopers.domain.order;

public class OrderEvent {

    public record Created(Long userId, String orderId, Long totalAmount) {

        public static Created from(Order order) {
            return new Created(order.userId(), order.orderId(), order.originalAmount() - order.discountAmount());
        }
    }
}
