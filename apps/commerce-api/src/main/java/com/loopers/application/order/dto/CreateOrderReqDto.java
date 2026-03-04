package com.loopers.application.order.dto;

import java.util.List;

public record CreateOrderReqDto(List<OrderItemReqDto> items, Long userCouponId) {

    public record OrderItemReqDto(Long productId, int quantity) {
    }
}
