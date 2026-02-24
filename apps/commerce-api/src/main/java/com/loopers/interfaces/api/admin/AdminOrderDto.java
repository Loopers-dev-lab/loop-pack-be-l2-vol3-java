package com.loopers.interfaces.api.admin;

import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public class AdminOrderDto {

    public record OrderItemResponse(
            Long optionId,
            String productName,
            String optionName,
            BigDecimal price,
            int quantity,
            BigDecimal totalPrice
    ) {
        public static OrderItemResponse from(OrderInfo.OrderItemInfo info) {
            return new OrderItemResponse(
                    info.getOptionId(),
                    info.getProductName(),
                    info.getOptionName(),
                    info.getPrice().getAmount(),
                    info.getQuantity(),
                    info.getTotalPrice().getAmount()
            );
        }
    }

    public record OrderResponse(
            Long orderId,
            Long userId,
            OrderStatus status,
            BigDecimal totalAmount,
            List<OrderItemResponse> items
    ) {
        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                    info.getOrderId(),
                    info.getUserId(),
                    info.getStatus(),
                    info.getTotalAmount().getAmount(),
                    info.getItems().stream().map(OrderItemResponse::from).toList()
            );
        }
    }

    public record OrderListResponse(List<OrderResponse> orders) {
        public static OrderListResponse from(List<OrderInfo> infoList) {
            return new OrderListResponse(
                    infoList.stream().map(OrderResponse::from).toList()
            );
        }
    }
}
