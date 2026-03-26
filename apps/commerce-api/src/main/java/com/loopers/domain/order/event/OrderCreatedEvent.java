package com.loopers.domain.order.event;

import com.loopers.application.order.OrderInfo;
import com.loopers.support.enums.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 생성 이벤트.
 */
public record OrderCreatedEvent(
        Long orderId,
        Long userId,
        OrderType orderType,
        BigDecimal totalAmount,
        LocalDateTime expiresAt,
        List<OrderItemEvent> items
) {

    public record OrderItemEvent(
            Long orderId,
            int orderItemSeq,
            Long productId,
            int quantity,
            BigDecimal finalAmount
    ) {
        public static OrderItemEvent from(OrderInfo.OrderItemInfo itemInfo) {
            return new OrderItemEvent(
                    itemInfo.getOrderId(),
                    itemInfo.getOrderItemSeq(),
                    itemInfo.getProductId(),
                    itemInfo.getQuantity(),
                    itemInfo.getFinalAmount()
            );
        }
    }

    public static OrderCreatedEvent from(OrderInfo orderInfo) {
        return new OrderCreatedEvent(
                orderInfo.getOrderId(),
                orderInfo.getUserId(),
                orderInfo.getOrderType(),
                orderInfo.getTotalAmount(),
                orderInfo.getExpiresAt(),
                orderInfo.getItems() != null
                        ? orderInfo.getItems().stream().map(OrderItemEvent::from).toList()
                        : List.of()
        );
    }
}
