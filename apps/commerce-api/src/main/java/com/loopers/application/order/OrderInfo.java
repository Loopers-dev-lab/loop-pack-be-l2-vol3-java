package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderInfo(
    Long orderId,
    Long userId,
    String status,
    int totalAmount,
    int discountAmount,
    int finalAmount,
    List<OrderItemInfo> items,
    ZonedDateTime createdAt
) {

    public static OrderInfo from(OrderModel order, List<OrderItemModel> items) {
        List<OrderItemInfo> itemInfos = items.stream()
            .map(OrderItemInfo::from)
            .toList();
        return new OrderInfo(
            order.getId(), order.userId(), order.status().name(),
            order.totalAmount().value(), order.discountAmount().value(),
            order.finalAmount().value(), itemInfos, order.getCreatedAt()
        );
    }

    public static OrderInfo summaryFrom(OrderModel order) {
        return new OrderInfo(
            order.getId(), order.userId(), order.status().name(),
            order.totalAmount().value(), order.discountAmount().value(),
            order.finalAmount().value(), List.of(), order.getCreatedAt()
        );
    }

    public record OrderItemInfo(
        Long productId, String productName, int productPrice, int quantity, int subtotal
    ) {
        public static OrderItemInfo from(OrderItemModel item) {
            return new OrderItemInfo(
                item.productId(), item.productName(),
                item.productPrice().value(), item.quantity(), item.subtotal().value()
            );
        }
    }
}
