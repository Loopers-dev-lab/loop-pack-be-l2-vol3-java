package com.loopers.infrastructure.payment.toss.dto;

public record TossConfirmRequest(
        String paymentKey,
        String orderId,
        Long amount
) {
}
