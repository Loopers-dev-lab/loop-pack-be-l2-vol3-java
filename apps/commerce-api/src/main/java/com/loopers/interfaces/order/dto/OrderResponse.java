package com.loopers.interfaces.order.dto;

import com.loopers.application.order.dto.OrderResult;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderResponse {

    public record OrderSummary(
        Long orderId,
        int totalPrice,
        int originalTotalPrice,
        int discountAmount,
        String status,
        ZonedDateTime createdAt
    ) {
        public static OrderSummary from(OrderResult.OrderSummary result) {
            return new OrderSummary(
                    result.orderId(),
                    result.totalPrice(),
                    result.originalTotalPrice(),
                    result.discountAmount(),
                    result.status(),
                    result.createdAt());
        }
    }

    public record OrderDetail(
        Long orderId,
        Long userId,
        int totalPrice,
        String status,
        ZonedDateTime createdAt,
        List<OrderItemDetail> items
    ) {
        public static OrderDetail from(OrderResult.OrderDetail result) {
            return new OrderDetail(
                    result.orderId(),
                    result.userId(),
                    result.totalPrice(),
                    result.status(),
                    result.createdAt(),
                    result.items().stream().map(OrderItemDetail::from).toList());
        }
    }

    public record OrderItemDetail(
        Long orderItemId,
        Long productId,
        String productName,
        String brandName,
        int orderPrice,
        int quantity
    ) {
        public static OrderItemDetail from(OrderResult.OrderItemDetail result) {
            return new OrderItemDetail(
                    result.orderItemId(),
                    result.productId(),
                    result.productName(),
                    result.brandName(),
                    result.orderPrice(),
                    result.quantity());
        }
    }

    public record ListResponse(
        List<OrderSummary> items
    ) {}

    public record PageResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<OrderSummary> items
    ) {}
}
