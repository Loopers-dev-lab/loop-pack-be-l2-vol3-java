package com.loopers.application.order;

import java.time.LocalDateTime;
import java.util.List;

import com.loopers.application.order.OrderDetailResult.OrderItemResult;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;

public record AdminOrderDetailResult(
        Long id,
        String name,
        OrderStatus status,
        Long totalPrice,
        LocalDateTime orderedAt,
        List<OrderItemResult> orderItems,
        Orderer orderer
) {

    public static AdminOrderDetailResult of(Order order, String maskedOrdererName) {
        return new AdminOrderDetailResult(
                order.getId(),
                order.getName(),
                order.getStatus(),
                order.getTotalPrice().getAmount(),
                order.getOrderedAt(),
                order.getOrderItems().stream()
                        .map(OrderItemResult::from)
                        .toList(),
                new Orderer(order.getUserId(), maskedOrdererName)
        );
    }
    
    public record Orderer(Long id, String name) {
        
    }
}