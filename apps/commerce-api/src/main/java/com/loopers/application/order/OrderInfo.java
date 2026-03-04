package com.loopers.application.order;

import com.loopers.domain.order.Order;

import java.time.ZonedDateTime;

public record OrderInfo(
        Long id,
        Long userId,
        Order.Status status,
        Long totalAmount,
        ZonedDateTime createdAt
) {
    public static OrderInfo from(Order order) {
        return new OrderInfo(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}
