package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
        Long id,
        BigDecimal totalAmount,
        List<OrderItemInfo> orderItems,
        ZonedDateTime createdAt
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
                order.getTotalAmount(),
                items,
                order.getCreatedAt()
        );
    }
}
