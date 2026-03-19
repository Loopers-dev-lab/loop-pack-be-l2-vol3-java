package com.loopers.infrastructure.payment.pgsimulator.dto;

public record PgSimulatorRequestPaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        int amount,
        String callbackUrl
) {
}
