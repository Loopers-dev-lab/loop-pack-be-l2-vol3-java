package com.loopers.interfaces.api.order;

import com.loopers.domain.PageResult;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    public record CreateOrderRequest(
        @NotEmpty(message = "주문 항목은 하나 이상이어야 합니다.")
        @Valid
        List<OrderItemRequest> items,

        @Min(value = 1, message = "쿠폰 ID는 1 이상이어야 합니다.")
        Long couponId
    ) {}

    public record OrderItemRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId,

        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
        int quantity
    ) {}

    public record OrderResponse(
        Long orderId,
        int totalPrice,
        int originalPrice,
        int discountAmount,
        String status,
        ZonedDateTime createdAt
    ) {
        public static OrderResponse from(Order order) {
            return new OrderResponse(
                order.getId(), order.getTotalPrice().amount(),
                order.getOriginalPrice().amount(), order.getDiscountAmount().amount(),
                order.getStatus().name(), order.getCreatedAt()
            );
        }
    }

    public record OrderDetailResponse(
        Long orderId,
        int totalPrice,
        int originalPrice,
        int discountAmount,
        String status,
        ZonedDateTime createdAt,
        List<OrderItemResponse> items
    ) {
        public static OrderDetailResponse from(Order order) {
            List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderDetailResponse(
                order.getId(), order.getTotalPrice().amount(),
                order.getOriginalPrice().amount(), order.getDiscountAmount().amount(),
                order.getStatus().name(), order.getCreatedAt(), items
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
