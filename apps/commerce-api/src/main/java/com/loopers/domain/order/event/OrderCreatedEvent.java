package com.loopers.domain.order.event;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
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
        public static OrderItemEvent from(OrderItemModel item) {
            return new OrderItemEvent(
                    item.getOrderId(),
                    item.getOrderItemSeq(),
                    item.getProductId(),
                    item.getQuantity(),
                    item.getFinalAmount()
            );
        }
    }

    public static OrderCreatedEvent from(OrderModel order, List<OrderItemModel> items) {
        return new OrderCreatedEvent(
                order.getOrderId(),
                order.getUserId(),
                order.getOrderType(),
                order.getTotalAmount(),
                order.getExpiresAt(),
                items != null
                        ? items.stream().map(OrderItemEvent::from).toList()
                        : List.of()
        );
    }
}
