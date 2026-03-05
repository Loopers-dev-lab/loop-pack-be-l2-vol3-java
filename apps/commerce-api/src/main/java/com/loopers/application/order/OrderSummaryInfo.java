package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;

import java.time.ZonedDateTime;

public record OrderSummaryInfo(
    Long id,
    Long userId,
    Long originalAmount,
    Long discountAmount,
    Long totalAmount,
    ZonedDateTime orderedAt
) {
    public static OrderSummaryInfo from(OrderModel order) {
        return new OrderSummaryInfo(
            order.getId(),
            order.getUserId(),
            order.getOriginalAmount(),
            order.getDiscountAmount(),
            order.getTotalAmount(),
            order.getCreatedAt()
        );
    }
}
