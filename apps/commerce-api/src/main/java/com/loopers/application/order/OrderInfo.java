package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderInfo(
        Long id,
        Long userId,
        BigDecimal totalAmount,
        List<OrderItemInfo> orderItems,
        LocalDateTime createdAt
) {

    public record OrderItemInfo(
            Long productId,
            String productName,
            BigDecimal price,
            Integer quantity,
            BigDecimal orderPrice
    ) {

        public static OrderItemInfo from(OrderItem orderItem) {
            return new OrderItemInfo(
                    orderItem.getProductId(),
                    orderItem.getProductName(),
                    orderItem.getPrice(),
                    orderItem.getQuantity(),
                    orderItem.getOrderPrice()
            );
        }
    }

    public static OrderInfo from(Order order) {
        List<OrderItemInfo> items = order.getOrderItems().stream()
                .map(OrderItemInfo::from)
                .toList();
        return new OrderInfo(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                items,
                order.getCreatedAt().toLocalDateTime()
        );
    }

    public record OrderSummary(
            Long id,
            BigDecimal totalAmount,
            LocalDateTime createdAt
    ) {

        public static OrderSummary from(Order order) {
            return new OrderSummary(
                    order.getId(),
                    order.getTotalAmount(),
                    order.getCreatedAt().toLocalDateTime()
            );
        }
    }

    public record OrderAdminSummary(
            Long id,
            Long userId,
            BigDecimal totalAmount,
            LocalDateTime createdAt
    ) {

        public static OrderAdminSummary from(Order order) {
            return new OrderAdminSummary(
                    order.getId(),
                    order.getUserId(),
                    order.getTotalAmount(),
                    order.getCreatedAt().toLocalDateTime()
            );
        }
    }
}
