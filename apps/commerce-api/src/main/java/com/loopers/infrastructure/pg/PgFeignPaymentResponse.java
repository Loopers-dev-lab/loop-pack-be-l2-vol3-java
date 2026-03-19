package com.loopers.infrastructure.pg;

public record PgFeignPaymentResponse(
        String transactionKey,
        String orderId,
        String status,
        String message
) {
}
