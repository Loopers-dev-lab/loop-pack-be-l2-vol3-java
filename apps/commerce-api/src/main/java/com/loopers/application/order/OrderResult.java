package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 주문 응답 DTO.
 */
public record OrderResult(
    Long id,
    Long userId,
    List<OrderItemResult> items,
    Long totalPrice,
    OrderStatus status,
    ZonedDateTime createdAt
) {
    public static OrderResult from(Order order) {
        List<OrderItemResult> itemResults = order.getItems().stream()
            .map(OrderItemResult::from)
            .toList();

        return new OrderResult(
            order.getId(),
            order.getUserId(),
            itemResults,
            order.getTotalPrice().amount(),
            order.getStatus(),
            order.getCreatedAt()
        );
    }
}
