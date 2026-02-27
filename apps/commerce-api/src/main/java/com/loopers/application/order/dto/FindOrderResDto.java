package com.loopers.application.order.dto;

import com.loopers.domain.order.model.Orders;

import java.util.List;

public record FindOrderResDto(
        Long id,
        int totalPrice,
        List<FindOrderProductResDto> orderProducts
) {
    public static FindOrderResDto from(Orders orders) {
        List<FindOrderProductResDto> products = orders.getOrderProducts().stream()
                .map(FindOrderProductResDto::from)
                .toList();
        return new FindOrderResDto(
                orders.getId(),
                orders.getTotalPrice().value(),
                products
        );
    }
}
