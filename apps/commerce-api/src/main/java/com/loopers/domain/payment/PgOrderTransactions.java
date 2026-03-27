package com.loopers.domain.payment;

import java.util.List;

public record PgOrderTransactions(
        String orderId,
        List<PgTransactionSummary> transactions) {

    public record PgTransactionSummary(
            String transactionKey,
            String status,
            String reason) {
    }
}
