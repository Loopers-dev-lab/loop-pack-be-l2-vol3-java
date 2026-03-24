package com.loopers.infrastructure.pg;

public record PgFeignPaymentStatusResponse(
        String transactionKey,
        String orderId,
        String status,
        String message
) {
}
