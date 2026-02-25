package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderDetailInfo(
    Long id,
    Long userId,
    Long totalAmount,
    ZonedDateTime orderedAt,
    List<OrderItemInfo> items
) {
    public static OrderDetailInfo from(OrderModel order) {
        return new OrderDetailInfo(
            order.getId(),
            order.getUserId(),
            order.getTotalAmount(),
            order.getCreatedAt(),
            order.getOrderItems().stream()
                .map(OrderItemInfo::from)
                .toList()
        );
    }
}
