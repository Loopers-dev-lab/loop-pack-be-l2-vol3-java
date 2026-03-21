package com.loopers.infrastructure.payment.pgsimulator.dto;

public record PgSimulatorTransactionResponse(
        String transactionKey,
        String orderId,
        String status,
        String reason
) {
}
