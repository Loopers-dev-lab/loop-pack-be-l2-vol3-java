package com.loopers.domain.payment.gateway;

public record PaymentConfirmCommand(
        String paymentKey,
        String orderId,
        Long amount
) {
}
