package com.loopers.infrastructure.pg.dto;

public record PgPaymentResponse(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        String amount,
        String status
) {
    public static PgPaymentResponse empty() {
        return new PgPaymentResponse(null, null, null, null, null, null);
    }
}
