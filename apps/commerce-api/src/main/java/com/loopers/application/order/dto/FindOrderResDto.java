package com.loopers.application.order.dto;

import com.loopers.domain.order.model.Orders;

import java.util.List;

public record FindOrderResDto(
        Long id,
        String orderNumber,
        int totalPrice,
        int discountAmount,
        Long userCouponId,
        List<FindOrderProductResDto> orderProducts
) {
    public static FindOrderResDto from(Orders orders) {
        List<FindOrderProductResDto> products = orders.getOrderProducts().stream()
                .map(FindOrderProductResDto::from)
                .toList();
        return new FindOrderResDto(
                orders.getId(),
                orders.getOrderNumber(),
                orders.getTotalPrice().value(),
                orders.getDiscountAmount().value(),
                orders.getUserCouponId(),
                products
        );
    }
}
