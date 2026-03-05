package com.loopers.interfaces.api.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class OrderDto {

    public record CreateRequest(
        @NotEmpty List<OrderItemRequest> items,
        Long couponId
    ) {}

    public record OrderItemRequest(
        @NotNull Long productId,
        @Min(1) int quantity
    ) {}

    public record OrderResponse(
        Long id,
        Long memberId,
        String status,
        int totalPrice,
        int originalTotalPrice,
        int discountAmount,
        Long couponIssueId,
        List<OrderItemResponse> items
    ) {
        public static OrderResponse from(Order order) {
            List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();
            return new OrderResponse(
                order.getId(),
                order.getMemberId(),
                order.getStatus().name(),
                order.getTotalPrice(),
                order.getOriginalTotalPrice(),
                order.getDiscountAmount(),
                order.getCouponIssueId(),
                itemResponses
            );
        }
    }

    public record OrderItemResponse(
        Long productId,
        String productName,
        int productPrice,
        String brandName,
        int quantity,
        int subtotal
    ) {
        public static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                item.getProductId(),
                item.getProductName(),
                item.getProductPrice(),
                item.getBrandName(),
                item.getQuantity(),
                item.getSubtotal()
            );
        }
    }
}
