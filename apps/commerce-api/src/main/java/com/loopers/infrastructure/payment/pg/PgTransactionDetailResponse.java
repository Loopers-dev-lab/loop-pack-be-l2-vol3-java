package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.TransactionDetailResult;

public record PgTransactionDetailResponse(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String status,
        String reason
) {

    public TransactionDetailResult toDomain() {
        return new TransactionDetailResult(
                transactionKey,
                orderId,
                cardType,
                cardNo,
                amount,
                status,
                reason
        );
    }
}
