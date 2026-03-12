package com.loopers.application.order;

/**
 * 주문 항목 요청 DTO.
 */
public record OrderItemRequest(
    Long productId,
    int quantity
) {
}
