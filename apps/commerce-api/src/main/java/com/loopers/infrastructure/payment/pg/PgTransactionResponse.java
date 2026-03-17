package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.TransactionResult;

public record PgTransactionResponse(
        String transactionKey,
        String status,
        String reason
) {

    public TransactionResult toDomain() {
        return new TransactionResult(transactionKey, status, reason);
    }
}
