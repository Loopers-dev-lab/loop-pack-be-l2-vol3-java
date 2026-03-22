package com.loopers.domain.payment;

public record TransactionDetailResult(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String status,
        String reason
) {
}
