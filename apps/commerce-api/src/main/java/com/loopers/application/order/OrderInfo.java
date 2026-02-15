package com.loopers.application.order;

import com.loopers.domain.order.Order;

import java.time.ZonedDateTime;

public record OrderInfo(
    Long orderId,
    Long userId,
    int totalPrice,
    String status,
    ZonedDateTime createdAt
) {
    public static OrderInfo from(Order order) {
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getTotalPrice().amount(),
            order.getStatus().name(),
            order.getCreatedAt()
        );
    }
}
