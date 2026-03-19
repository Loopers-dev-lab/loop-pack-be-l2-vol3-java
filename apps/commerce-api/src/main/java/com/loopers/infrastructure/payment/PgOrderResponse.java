package com.loopers.infrastructure.payment;

import java.util.List;

public record PgOrderResponse(
        Meta meta,
        Data data
) {
    public record Meta(String result, String errorCode, String message) {
    }

    public record Data(
            String orderId,
            List<Transaction> transactions
    ) {
    }

    public record Transaction(
            String transactionKey,
            String status,
            String reason
    ) {
    }
}
