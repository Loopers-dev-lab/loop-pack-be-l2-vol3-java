package com.loopers.application.order;

import com.loopers.domain.order.Order;

public record PlaceOrderResult(
        Long orderId,
        String orderKey
) {

    public static PlaceOrderResult from(Order order) {
        return new PlaceOrderResult(order.getId(), order.getOrderKey());
    }
}
