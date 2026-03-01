package com.loopers.application.order;

import java.time.LocalDateTime;
import java.util.List;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;

public record OrderDetailResult(
        Long id,
        String name,
        OrderStatus status,
        Long totalPrice,
        LocalDateTime orderedAt,
        List<OrderItemResult> orderItems
) {

    public static OrderDetailResult from(Order order) {
        return new OrderDetailResult(
                order.getId(),
                order.getName(),
                order.getStatus(),
                order.getTotalPrice().getAmount(),
                order.getOrderedAt(),
                order.getOrderItems().stream()
                        .map(OrderItemResult::from)
                        .toList()
        );
    }

    public record OrderItemResult(
            Long productId,
            String productName,
            String productThumbnailUrl,
            Long productPrice,
            Long quantity,
            Long subtotal
    ) {

        public static OrderItemResult from(OrderItem item) {
            return new OrderItemResult(
                    item.getProductId(),
                    item.getProductName(),
                    item.getProductThumbnailUrl(),
                    item.getProductPrice().getAmount(),
                    item.getQuantity(),
                    item.calculateSubtotal().getAmount()
            );
        }
    }
}