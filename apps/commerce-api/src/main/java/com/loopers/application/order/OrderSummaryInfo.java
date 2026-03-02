package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;

import java.time.ZonedDateTime;

public record OrderSummaryInfo(
    Long id,
    Long userId,
    Long totalAmount,
    ZonedDateTime orderedAt
) {
    public static OrderSummaryInfo from(OrderModel order) {
        return new OrderSummaryInfo(
            order.getId(),
            order.getUserId(),
            order.getTotalAmount(),
            order.getCreatedAt()
        );
    }
}
