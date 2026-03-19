package com.loopers.infrastructure.payment.dto;

public record TossConfirmRequest(
        String paymentKey,
        String orderId,
        Long amount
) {
}
