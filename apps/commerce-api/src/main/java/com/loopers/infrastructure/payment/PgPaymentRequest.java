package com.loopers.infrastructure.payment;

public record PgPaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        long amount,
        String callbackUrl
) {
}
