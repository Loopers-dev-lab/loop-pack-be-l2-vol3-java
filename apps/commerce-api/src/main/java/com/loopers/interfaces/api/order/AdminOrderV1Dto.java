package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderDetailInfo;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.PageResult;

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
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(info.orderId(), info.userId(), info.totalPrice(), info.status(), info.createdAt());
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
        public static OrderDetailResponse from(OrderDetailInfo info) {
            List<OrderItemResponse> items = info.items().stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderDetailResponse(
                info.orderId(), info.userId(), info.totalPrice(), info.status(), info.createdAt(), items
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
        public static OrderItemResponse from(OrderDetailInfo.OrderItemInfo info) {
            return new OrderItemResponse(
                info.productId(), info.productName(), info.productPrice(),
                info.brandName(), info.quantity()
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
        public static OrderPageResponse from(PageResult<OrderInfo> result) {
            List<OrderResponse> content = result.items().stream()
                .map(OrderResponse::from)
                .toList();
            return new OrderPageResponse(content, result.page(), result.size(), result.totalElements(), result.totalPages());
        }
    }
}
