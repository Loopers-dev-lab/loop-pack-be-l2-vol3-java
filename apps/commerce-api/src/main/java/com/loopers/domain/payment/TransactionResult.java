package com.loopers.domain.payment;

public record TransactionResult(
        String transactionKey,
        String status,
        String reason
) {
}
