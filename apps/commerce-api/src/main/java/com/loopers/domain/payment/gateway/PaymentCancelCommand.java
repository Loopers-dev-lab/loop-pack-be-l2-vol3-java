package com.loopers.domain.payment.gateway;

public record PaymentCancelCommand(
        String cancelReason,
        Long cancelAmount
) {
}
