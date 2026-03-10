package com.loopers.application.order;

import com.loopers.domain.order.Order;

import java.time.ZonedDateTime;

public record OrderInfo(
        Long id,
        Long userId,
        Order.Status status,
        Long originalAmount,
        Long discountAmount,
        Long finalAmount,
        Long issuedCouponId,
        ZonedDateTime createdAt
) {
    public static OrderInfo from(Order order) {
        return new OrderInfo(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getOriginalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                order.getIssuedCouponId(),
                order.getCreatedAt()
        );
    }
}
