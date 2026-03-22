package com.loopers.interfaces.api.order.v1;

import java.time.LocalDateTime;
import java.util.List;

import com.loopers.application.order.AdminOrderDetailResult;
import com.loopers.application.order.OrderDetailResult;
import com.loopers.application.order.OrderResult;
import com.loopers.domain.order.OrderStatus;

public class AdminOrderDto {

    public record OrderListResponse(
            Long orderId,
            String orderKey,
            String name,
            OrderStatus status,
            Long originalTotalPrice,
            Long discountAmount,
            Long totalPrice,
            LocalDateTime orderedAt
    ) {

        public static OrderListResponse from(OrderResult result) {
            return new OrderListResponse(
                    result.id(),
                    result.orderKey(),
                    result.name(),
                    result.status(),
                    result.originalTotalPrice(),
                    result.discountAmount(),
                    result.totalPrice(),
                    result.orderedAt()
            );
        }
    }

    public record OrderDetailResponse(
            Long orderId,
            String orderKey,
            String name,
            OrderStatus status,
            Long originalTotalPrice,
            Long discountAmount,
            Long totalPrice,
            LocalDateTime orderedAt,
            List<OrderItemResponse> orderItems,
            OrdererResponse orderer
    ) {

        public static OrderDetailResponse from(AdminOrderDetailResult result) {
            return new OrderDetailResponse(
                    result.id(),
                    result.orderKey(),
                    result.name(),
                    result.status(),
                    result.originalTotalPrice(),
                    result.discountAmount(),
                    result.totalPrice(),
                    result.orderedAt(),
                    result.orderItems().stream()
                            .map(OrderItemResponse::from)
                            .toList(),
                    OrdererResponse.from(result.orderer())
            );
        }
    }

    public record OrdererResponse(
            Long id,
            String name
    ) {

        public static OrdererResponse from(AdminOrderDetailResult.Orderer orderer) {
            return new OrdererResponse(orderer.id(), orderer.name());
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
