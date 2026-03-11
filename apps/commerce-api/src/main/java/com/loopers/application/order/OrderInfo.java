package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import java.time.ZonedDateTime;

public record OrderInfo(
    Long id,
    Long userId,
    OrderStatus status,
    Integer totalPrice,
    ZonedDateTime orderDt
) {
    public static OrderInfo from(Order order) {
        return new OrderInfo(
            order.getId(),
            order.getRefUserId(),
            order.getStatus(),
            order.getTotalPrice().value(),
            order.getOrderDt()
        );
    }
}
