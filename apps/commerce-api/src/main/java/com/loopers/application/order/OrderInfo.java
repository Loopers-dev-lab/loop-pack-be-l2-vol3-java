package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;

import java.time.ZonedDateTime;
import java.util.List;

public class OrderInfo {

    public record Summary(
        Long id, int totalAmount, String status, int itemCount, ZonedDateTime createdAt
    ) {
        public static Summary from(OrderModel order) {
            return new Summary(
                order.getId(),
                order.getTotalAmount().value(),
                order.getStatus().name(),
                order.getOrderItems().size(),
                order.getCreatedAt()
            );
        }
    }

    public record Detail(
        Long id, Long memberId, int totalAmount, String status,
        List<OrderItemInfo> orderItems, ZonedDateTime createdAt
    ) {
        public static Detail from(OrderModel order) {
            List<OrderItemInfo> items = order.getOrderItems().stream()
                .map(OrderItemInfo::from)
                .toList();
            return new Detail(
                order.getId(),
                order.getMemberId(),
                order.getTotalAmount().value(),
                order.getStatus().name(),
                items,
                order.getCreatedAt()
            );
        }
    }

    public record OrderItemInfo(
        Long id, Long productId, String productName, int productPrice, int quantity, int subtotal
    ) {
        public static OrderItemInfo from(OrderItemModel item) {
            return new OrderItemInfo(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getProductPrice().value(),
                item.getQuantity(),
                item.getSubtotal().value()
            );
        }
    }
}
