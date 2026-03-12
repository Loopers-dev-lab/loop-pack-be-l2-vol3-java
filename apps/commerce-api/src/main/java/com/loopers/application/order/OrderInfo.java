package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
        Long id,
        Long userId,
        Long userCouponId,
        int originalAmount,
        int discountAmount,
        int finalAmount,
        ZonedDateTime createdAt,
        List<OrderItemInfo> items
) {

    public static OrderInfo of(Order order) {
        List<OrderItemInfo> items = order.getOrderItems().stream()
                .map(OrderItemInfo::of)
                .toList();
        return new OrderInfo(
                order.getId(),
                order.getUserId(),
                order.getUserCouponId(),
                order.getOriginalAmount().getAmount(),
                order.getDiscountAmount().getAmount(),
                order.getFinalAmount().getAmount(),
                order.getCreatedAt(),
                items
        );
    }

    public record OrderItemInfo(
            Long orderItemId,
            Long productId,
            String productName,
            String brandName,
            int price,
            int quantity
    ) {

        public static OrderItemInfo of(OrderItem item) {
            return new OrderItemInfo(
                    item.getId(),
                    item.getProductId(),
                    item.getProductName(),
                    item.getBrandName(),
                    item.getPrice().getAmount(),
                    item.getQuantity().getValue()
            );
        }
    }
}
