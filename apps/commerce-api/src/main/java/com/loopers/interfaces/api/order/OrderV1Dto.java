package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderV1Dto {

    // Response

    public record OrderResponse(
            Long id,
            BigDecimal totalAmount,
            List<OrderItemResponse> orderItems,
            ZonedDateTime createdAt
    ) {

        public static OrderResponse from(OrderInfo info) {
            List<OrderItemResponse> items = info.orderItems().stream()
                    .map(OrderItemResponse::from)
                    .toList();
            return new OrderResponse(
                    info.id(),
                    info.totalAmount(),
                    items,
                    info.createdAt()
            );
        }
    }

    public record OrderItemResponse(
            Long productId,
            String productName,
            BigDecimal price,
            Integer quantity,
            BigDecimal orderPrice
    ) {

        public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
            return new OrderItemResponse(
                    item.productId(),
                    item.productName(),
                    item.price(),
                    item.quantity(),
                    item.orderPrice()
            );
        }
    }
}
