package com.loopers.application.order;

import java.time.LocalDateTime;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;

public record OrderResult(
        Long id,
        String orderKey,
        String name,
        OrderStatus status,
        Long originalTotalPrice,
        Long discountAmount,
        Long totalPrice,
        LocalDateTime orderedAt
) {

    public static OrderResult from(Order order) {
        return new OrderResult(
                order.getId(),
                order.getOrderKey(),
                order.getName(),
                order.getStatus(),
                order.getOriginalTotalPrice().getAmount(),
                order.getDiscountAmount().getAmount(),
                order.getTotalPrice().getAmount(),
                order.getOrderedAt()
        );
    }
}
