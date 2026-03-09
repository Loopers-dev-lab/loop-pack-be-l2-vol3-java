package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 주문 응답용 애플리케이션 DTO.
 * Controller 응답에 사용하며, interfaces DTO와 분리한다.
 */
public record OrderInfo(
    Long id,
    Long userId,
    String status,
    ZonedDateTime orderedAt,
    Long issuedCouponId,
    BigDecimal amountBeforeDiscount,
    BigDecimal discountAmount,
    BigDecimal finalAmount,
    List<OrderItemInfo> items
) {
    public static OrderInfo from(OrderModel order) {
        if (order == null) {
            return null;
        }
        List<OrderItemInfo> items = order.getOrderItems().stream()
            .map(OrderItemInfo::from)
            .toList();
        return new OrderInfo(
            order.getId(),
            order.getUserId(),
            order.getStatus().name(),
            order.getOrderedAt(),
            order.getIssuedCouponId(),
            order.getAmountBeforeDiscount(),
            order.getDiscountAmount(),
            order.getFinalAmount(),
            items
        );
    }
}
