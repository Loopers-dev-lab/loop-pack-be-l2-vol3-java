package com.loopers.domain.payment;

public record PgPaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        long amount,
        String callbackUrl,
        String userId) {
}
