package com.loopers.domain.order;

public record OrderCreatedEvent(Long orderId, Long userId, Long totalAmount) {

    public static OrderCreatedEvent from(Order order) {
        return new OrderCreatedEvent(order.getId(), order.getUserId(), order.getTotalAmount());
    }
}
