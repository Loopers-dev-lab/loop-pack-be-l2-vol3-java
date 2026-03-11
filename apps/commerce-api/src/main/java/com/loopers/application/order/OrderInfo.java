package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import java.time.ZonedDateTime;

public record OrderInfo(
    Long id,
    Long userId,
    Long couponId,
    OrderStatus status,
    Integer originalPrice,
    Integer discountAmount,
    Integer totalPrice,
    ZonedDateTime orderDt
) {
    public static OrderInfo from(Order order) {
        return new OrderInfo(
            order.getId(),
            order.getRefUserId(),
            order.getRefCouponId(),
            order.getStatus(),
            order.getOriginalPrice().value(),
            order.getDiscountAmount().value(),
            order.getTotalPrice().value(),
            order.getOrderDt()
        );
    }
}
