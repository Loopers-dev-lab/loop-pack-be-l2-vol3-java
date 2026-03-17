package com.loopers.infrastructure.payment.dto;

public record PgPaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
) {

    public static PgPaymentRequest of(Long orderId, String cardType, String cardNo, Long amount, String callbackUrl) {
        return new PgPaymentRequest(
                String.valueOf(orderId),
                cardType,
                cardNo,
                amount,
                callbackUrl
        );
    }
}
