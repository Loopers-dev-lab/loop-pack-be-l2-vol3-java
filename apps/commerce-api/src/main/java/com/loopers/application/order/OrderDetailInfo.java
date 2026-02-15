package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderDetailInfo(
    Long orderId,
    Long userId,
    int totalPrice,
    String status,
    ZonedDateTime createdAt,
    List<OrderItemInfo> items
) {
    public static OrderDetailInfo from(Order order, List<OrderItem> items) {
        List<OrderItemInfo> itemInfos = items.stream()
            .map(OrderItemInfo::from)
            .toList();
        return new OrderDetailInfo(
            order.getId(),
            order.getUserId(),
            order.getTotalPrice().amount(),
            order.getStatus().name(),
            order.getCreatedAt(),
            itemInfos
        );
    }

    public record OrderItemInfo(
        Long productId,
        String productName,
        int productPrice,
        String brandName,
        int quantity
    ) {
        public static OrderItemInfo from(OrderItem item) {
            return new OrderItemInfo(
                item.getProductId(),
                item.getProductName(),
                item.getProductPrice().amount(),
                item.getBrandName(),
                item.getQuantity().value()
            );
        }
    }
}
