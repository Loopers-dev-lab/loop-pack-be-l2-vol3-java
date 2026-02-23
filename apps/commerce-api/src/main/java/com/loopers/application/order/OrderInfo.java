package com.loopers.application.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class OrderInfo {
    private final Long orderId;
    private final Long userId;
    private final OrderStatus status;
    private final Money totalAmount;
    private final List<OrderItemInfo> items;

    @Getter
    @Builder
    public static class OrderItemInfo {
        private final Long optionId;
        private final String productName;
        private final String optionName;
        private final Money price;
        private final int quantity;
        private final Money totalPrice;

        public static OrderItemInfo from(OrderItem item) {
            return OrderItemInfo.builder()
                    .optionId(item.getOptionId())
                    .productName(item.getProductName())
                    .optionName(item.getOptionName())
                    .price(item.getPrice())
                    .quantity(item.getQuantity())
                    .totalPrice(item.getTotalPrice())
                    .build();
        }
    }

    public static OrderInfo from(Order order) {
        return OrderInfo.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .items(order.getOrderItems().stream().map(OrderItemInfo::from).toList())
                .build();
    }
}
