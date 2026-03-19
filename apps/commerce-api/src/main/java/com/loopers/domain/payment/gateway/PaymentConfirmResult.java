package com.loopers.domain.payment.gateway;

public record PaymentConfirmResult(
        boolean success,
        String paymentKey,
        String message
) {
}
