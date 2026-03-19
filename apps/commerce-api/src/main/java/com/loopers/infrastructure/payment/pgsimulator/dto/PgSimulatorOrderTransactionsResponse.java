package com.loopers.infrastructure.payment.pgsimulator.dto;

import java.util.List;

public record PgSimulatorOrderTransactionsResponse(
        String orderId,
        List<PgSimulatorTransactionResponse> transactions
) {
}
