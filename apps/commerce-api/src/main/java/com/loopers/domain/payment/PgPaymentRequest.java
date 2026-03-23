package com.loopers.domain.payment;

public record PgPaymentRequest(
    String userId,
    String orderId,
    String cardType,
    String cardNo,
    Long amount,
    String callbackUrl
) {
    public static PgPaymentRequest of(String userId, Long orderId, String cardType,
                                       String cardNo, Long amount, String callbackUrl) {
        return new PgPaymentRequest(
            userId,
            String.valueOf(orderId),
            cardType,
            cardNo,
            amount,
            callbackUrl
        );
    }
}
