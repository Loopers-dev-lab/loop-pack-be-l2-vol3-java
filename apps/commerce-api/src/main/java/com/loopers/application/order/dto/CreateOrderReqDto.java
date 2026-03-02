package com.loopers.application.order.dto;

import java.util.List;

public record CreateOrderReqDto(List<OrderItemReqDto> items) {

    public record OrderItemReqDto(Long productId, int quantity) {
    }
}
