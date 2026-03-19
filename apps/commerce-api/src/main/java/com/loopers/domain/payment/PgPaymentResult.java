package com.loopers.domain.payment;

public record PgPaymentResult(
        boolean accepted,
        String transactionId,
        String message
) {

    public static PgPaymentResult fallback(String message) {
        return new PgPaymentResult(false, null, message);
    }
}
