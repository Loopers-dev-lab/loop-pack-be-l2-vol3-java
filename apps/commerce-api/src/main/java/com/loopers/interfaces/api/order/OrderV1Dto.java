package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemInfo;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record CreateRequest(
            @NotNull @NotEmpty List<OrderItemRequest> items
    ) {}

    public record OrderItemRequest(
            @NotNull Long productId,
            @NotNull @Min(1) Integer quantity
    ) {}

    public record OrderResponse(
            Long id,
            Long userId,
            String status,
            Long totalAmount,
            ZonedDateTime createdAt
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                    info.id(),
                    info.userId(),
                    info.status().name(),
                    info.totalAmount(),
                    info.createdAt()
            );
        }
    }

    public record OrderDetailResponse(
            Long id,
            Long userId,
            String status,
            Long totalAmount,
            ZonedDateTime createdAt,
            List<OrderItemResponse> items
    ) {
        public static OrderDetailResponse from(OrderInfo order, List<OrderItemInfo> items) {
            return new OrderDetailResponse(
                    order.id(),
                    order.userId(),
                    order.status().name(),
                    order.totalAmount(),
                    order.createdAt(),
                    items.stream().map(OrderItemResponse::from).toList()
            );
        }
    }

    public record OrderItemResponse(
            Long id,
            Long productId,
            String productName,
            Integer price,
            Integer quantity
    ) {
        public static OrderItemResponse from(OrderItemInfo info) {
            return new OrderItemResponse(
                    info.id(),
                    info.productId(),
                    info.productName(),
                    info.price(),
                    info.quantity()
            );
        }
    }
}
