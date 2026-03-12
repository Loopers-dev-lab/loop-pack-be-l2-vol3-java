package com.loopers.interfaces.api.order.admin;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderAdminV1Dto {

    public record OrderResponse(
        Long orderId,
        Long userId,
        String status,
        int totalAmount,
        List<OrderItemResponse> items,
        ZonedDateTime createdAt
    ) {

        public static OrderResponse from(OrderModel order, List<OrderItemModel> items) {
            List<OrderItemResponse> itemResponses = items.stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderResponse(
                order.getId(),
                order.userId(),
                order.status().name(),
                order.totalAmount().value(),
                itemResponses,
                order.getCreatedAt()
            );
        }
    }

    public record OrderSummaryResponse(
        Long orderId,
        Long userId,
        String status,
        int totalAmount,
        ZonedDateTime createdAt
    ) {

        public static OrderSummaryResponse from(OrderModel order) {
            return new OrderSummaryResponse(
                order.getId(),
                order.userId(),
                order.status().name(),
                order.totalAmount().value(),
                order.getCreatedAt()
            );
        }
    }

    public record OrderItemResponse(
        Long productId,
        String productName,
        int productPrice,
        int quantity,
        int subtotal
    ) {

        public static OrderItemResponse from(OrderItemModel item) {
            return new OrderItemResponse(
                item.productId(),
                item.productName(),
                item.productPrice().value(),
                item.quantity(),
                item.subtotal().value()
            );
        }
    }
}
