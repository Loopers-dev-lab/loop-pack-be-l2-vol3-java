package com.loopers.application.order.dto;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderResult {

    public record OrderSummary(
        Long orderId,
        int totalPrice,
        int originalTotalPrice,
        int discountAmount,
        String status,
        ZonedDateTime createdAt
    ) {
        public static OrderSummary from(OrderModel model) {
            return new OrderSummary(
                    model.getId(),
                    model.getTotalPrice(),
                    model.getOriginalTotalPrice(),
                    model.getDiscountAmount(),
                    model.getStatus().name(),
                    model.getCreatedAt());
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
        public static OrderDetail from(OrderModel model) {
            return new OrderDetail(
                    model.getId(),
                    model.getUserId(),
                    model.getTotalPrice(),
                    model.getStatus().name(),
                    model.getCreatedAt(),
                    model.getItems().stream().map(OrderItemDetail::from).toList());
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
        public static OrderItemDetail from(OrderItemModel model) {
            return new OrderItemDetail(
                    model.getId(),
                    model.getProductId(),
                    model.getProductName(),
                    model.getBrandName(),
                    model.getOrderPrice(),
                    model.getQuantity());
        }
    }
}
