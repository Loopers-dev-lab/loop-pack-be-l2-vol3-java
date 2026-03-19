package com.loopers.domain.payment.gateway;

public record PaymentCancelResult(
        boolean success,
        String message
) {
}
