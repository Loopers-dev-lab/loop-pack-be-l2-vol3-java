package com.loopers.interfaces.api.order.dto;

import com.loopers.application.order.dto.CreateOrderReqDto;

import java.util.List;

public record CreateOrderApiReqDto(
        List<OrderItemApiReqDto> items,
        Long userCouponId
) {

    public record OrderItemApiReqDto(
            Long productId,
            int quantity
    ) {}

    public CreateOrderReqDto toDto() {
        List<CreateOrderReqDto.OrderItemReqDto> orderItems = items.stream()
                .map(item -> new CreateOrderReqDto.OrderItemReqDto(item.productId(), item.quantity()))
                .toList();
        return new CreateOrderReqDto(orderItems, userCouponId);
    }
}
