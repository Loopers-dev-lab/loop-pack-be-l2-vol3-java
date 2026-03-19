package com.loopers.domain.payment;

public record PgPaymentStatusResult(
        String transactionId,
        String status,
        String message
) {
}
