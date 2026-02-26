package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
        Long id,
        Long userId,
        BigDecimal totalAmount,
        List<OrderItemInfo> orderItems,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static OrderInfo from(Order order) {
        List<OrderItemInfo> orderItemInfos = order.getOrderItems().stream()
                .map(OrderItemInfo::from)
                .toList();

        return new OrderInfo(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                orderItemInfos,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public record OrderItemInfo(
            Long id,
            Long productId,
            String productName,
            BigDecimal price,
            Integer quantity,
            BigDecimal totalPrice
    ) {
        public static OrderItemInfo from(OrderItem orderItem) {
            return new OrderItemInfo(
                    orderItem.getId(),
                    orderItem.getProductId(),
                    orderItem.getProductName(),
                    orderItem.getPrice(),
                    orderItem.getQuantity(),
                    orderItem.getTotalPrice()
            );
        }
    }
}
