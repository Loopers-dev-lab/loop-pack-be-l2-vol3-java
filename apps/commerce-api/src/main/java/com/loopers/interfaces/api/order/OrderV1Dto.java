package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderV1Dto {

    // Response

    public record OrderResponse(
            Long id,
            OrderStatus status,
            BigDecimal totalAmount,
            BigDecimal discountAmount,
            BigDecimal finalAmount,
            Long issuedCouponId,
            List<OrderItemResponse> orderItems,
            LocalDateTime createdAt
    ) {

        public static OrderResponse from(OrderInfo info) {
            List<OrderItemResponse> items = info.orderItems().stream()
                    .map(OrderItemResponse::from)
                    .toList();
            return new OrderResponse(
                    info.id(),
                    info.status(),
                    info.totalAmount(),
                    info.discountAmount(),
                    info.finalAmount(),
                    info.issuedCouponId(),
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
            OrderStatus status,
            BigDecimal totalAmount,
            BigDecimal discountAmount,
            BigDecimal finalAmount,
            Long issuedCouponId,
            LocalDateTime createdAt
    ) {

        public static OrderListResponse from(OrderInfo.OrderSummary summary) {
            return new OrderListResponse(
                    summary.id(),
                    summary.status(),
                    summary.totalAmount(),
                    summary.discountAmount(),
                    summary.finalAmount(),
                    summary.issuedCouponId(),
                    summary.createdAt()
            );
        }
    }
}
