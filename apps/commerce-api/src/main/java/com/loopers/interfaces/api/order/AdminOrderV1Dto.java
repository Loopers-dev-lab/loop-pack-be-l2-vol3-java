package com.loopers.interfaces.api.order;

import com.loopers.domain.PageResult;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;

import java.time.ZonedDateTime;
import java.util.List;

public class AdminOrderV1Dto {

    public record OrderResponse(
        Long orderId,
        Long userId,
        int totalPrice,
        String status,
        ZonedDateTime createdAt
    ) {
        public static OrderResponse from(Order order) {
            return new OrderResponse(order.getId(), order.getUserId(), order.getTotalPrice().amount(), order.getStatus().name(), order.getCreatedAt());
        }
    }

    public record OrderDetailResponse(
        Long orderId,
        Long userId,
        int totalPrice,
        String status,
        ZonedDateTime createdAt,
        List<OrderItemResponse> items
    ) {
        public static OrderDetailResponse from(Order order) {
            List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderDetailResponse(
                order.getId(), order.getUserId(), order.getTotalPrice().amount(), order.getStatus().name(), order.getCreatedAt(), items
            );
        }
    }

    public record OrderItemResponse(
        Long productId,
        String productName,
        int productPrice,
        String brandName,
        int quantity
    ) {
        public static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                item.getProductId(), item.getProductName(), item.getProductPrice().amount(),
                item.getBrandName(), item.getQuantity().value()
            );
        }
    }

    public record OrderPageResponse(
        List<OrderResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static OrderPageResponse from(PageResult<Order> result) {
            List<OrderResponse> content = result.items().stream()
                .map(OrderResponse::from)
                .toList();
            return new OrderPageResponse(content, result.page(), result.size(), result.totalElements(), result.totalPages());
        }
    }
}
