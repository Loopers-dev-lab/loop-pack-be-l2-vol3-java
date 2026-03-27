package com.loopers.domain.payment;

public record PgTransactionDetail(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        long amount,
        String status,
        String reason) {
}
