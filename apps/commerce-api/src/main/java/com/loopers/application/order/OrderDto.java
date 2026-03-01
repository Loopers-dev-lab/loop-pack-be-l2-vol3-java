package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderDto {

    public record CreateCommand(
        List<OrderLineCommand> items
    ) {
    }

    public record OrderLineCommand(
        Long productId,
        Integer quantity
    ) {
    }

    public record OrderItemInfo(
        Long productId,
        String productName,
        BigDecimal productPrice,
        Integer quantity,
        BigDecimal subtotal
    ) {
        public static OrderItemInfo from(OrderItem orderItem) {
            return new OrderItemInfo(
                orderItem.getProductId(),
                orderItem.getProductName(),
                orderItem.getProductPrice(),
                orderItem.getQuantity(),
                orderItem.getSubtotal()
            );
        }
    }

    public record OrderInfo(
        Long id,
        Long userId,
        OrderStatus status,
        BigDecimal totalAmount,
        ZonedDateTime createdAt,
        List<OrderItemInfo> items
    ) {
        public static OrderInfo from(Order order) {
            List<OrderItemInfo> items = order.getOrderItems().stream()
                .map(OrderItemInfo::from)
                .toList();

            return new OrderInfo(
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                items
            );
        }
    }
}
