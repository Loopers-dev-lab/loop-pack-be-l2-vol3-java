package com.loopers.domain.payment.gateway;

public record PaymentCancelCommand(
        String orderId,
        String cancelReason,
        Long cancelAmount
) {
}
