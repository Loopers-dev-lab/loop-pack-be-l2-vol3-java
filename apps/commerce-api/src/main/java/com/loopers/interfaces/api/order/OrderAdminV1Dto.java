package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderAdminV1Dto {

    // Response

    public record OrderResponse(
            Long id,
            Long userId,
            BigDecimal totalAmount,
            List<OrderItemResponse> orderItems,
            LocalDateTime createdAt
    ) {

        public static OrderResponse from(OrderInfo info) {
            List<OrderItemResponse> items = info.orderItems().stream()
                    .map(OrderItemResponse::from)
                    .toList();
            return new OrderResponse(
                    info.id(),
                    info.userId(),
                    info.totalAmount(),
                    items,
                    info.createdAt()
            );
        }
    }

    public record OrderItemResponse(
            Long productId,
            String productName,
            BigDecimal price,
            Integer quantity,
            BigDecimal orderPrice
    ) {

        public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
            return new OrderItemResponse(
                    item.productId(),
                    item.productName(),
                    item.price(),
                    item.quantity(),
                    item.orderPrice()
            );
        }
    }

    public record OrderListResponse(
            Long id,
            Long userId,
            BigDecimal totalAmount,
            LocalDateTime createdAt
    ) {

        public static OrderListResponse from(OrderInfo.OrderAdminSummary summary) {
            return new OrderListResponse(
                    summary.id(),
                    summary.userId(),
                    summary.totalAmount(),
                    summary.createdAt()
            );
        }
    }
}
