package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;

import java.util.List;

public record OrderResult(
    OrderModel order,
    List<OrderItemModel> items
) {

    public static OrderResult of(OrderModel order, List<OrderItemModel> items) {
        return new OrderResult(order, items);
    }
}
