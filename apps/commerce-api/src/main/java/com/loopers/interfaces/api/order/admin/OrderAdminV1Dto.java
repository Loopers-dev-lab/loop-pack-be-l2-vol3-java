package com.loopers.interfaces.api.order.admin;

import com.loopers.application.order.OrderInfo;

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

        public static OrderResponse from(OrderInfo info) {
            List<OrderItemResponse> items = info.items().stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderResponse(
                info.orderId(), info.userId(), info.status(),
                info.totalAmount(), items, info.createdAt()
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

        public static OrderSummaryResponse from(OrderInfo info) {
            return new OrderSummaryResponse(
                info.orderId(), info.userId(), info.status(),
                info.totalAmount(), info.createdAt()
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

        public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
            return new OrderItemResponse(
                item.productId(), item.productName(),
                item.productPrice(), item.quantity(), item.subtotal()
            );
        }
    }
}
