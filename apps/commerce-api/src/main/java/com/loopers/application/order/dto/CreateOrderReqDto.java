package com.loopers.application.order.dto;

import com.loopers.domain.order.model.OrderCommand;

import java.util.List;

public record CreateOrderReqDto(List<OrderItemReqDto> items, Long userCouponId) {

    public List<OrderCommand.OrderItem> toOrderItems() {
        return items.stream()
                .map(item -> new OrderCommand.OrderItem(item.productId(), item.quantity()))
                .toList();
    }

    public record OrderItemReqDto(Long productId, int quantity) {
    }
}
