package com.loopers.interfaces.api.order.v1;

import java.time.LocalDateTime;
import java.util.List;

import com.loopers.application.order.OrderDetailResult;
import com.loopers.application.order.OrderResult;
import com.loopers.domain.order.OrderStatus;

public class AdminOrderDto {

    public record OrderListResponse(
            Long orderId,
            String name,
            OrderStatus status,
            Long totalPrice,
            LocalDateTime orderedAt
    ) {

        public static OrderListResponse from(OrderResult result) {
            return new OrderListResponse(
                    result.id(),
                    result.name(),
                    result.status(),
                    result.totalPrice(),
                    result.orderedAt()
            );
        }
    }

    public record OrderDetailResponse(
            Long orderId,
            String name,
            OrderStatus status,
            Long totalPrice,
            LocalDateTime orderedAt,
            List<OrderItemResponse> orderItems
    ) {

        public static OrderDetailResponse from(OrderDetailResult result) {
            return new OrderDetailResponse(
                    result.id(),
                    result.name(),
                    result.status(),
                    result.totalPrice(),
                    result.orderedAt(),
                    result.orderItems().stream()
                            .map(OrderItemResponse::from)
                            .toList()
            );
        }
    }

    public record OrderItemResponse(
            Long productId,
            String productName,
            String productThumbnailUrl,
            Long productPrice,
            Long quantity,
            Long subtotal
    ) {

        public static OrderItemResponse from(OrderDetailResult.OrderItemResult result) {
            return new OrderItemResponse(
                    result.productId(),
                    result.productName(),
                    result.productThumbnailUrl(),
                    result.productPrice(),
                    result.quantity(),
                    result.subtotal()
            );
        }
    }
}