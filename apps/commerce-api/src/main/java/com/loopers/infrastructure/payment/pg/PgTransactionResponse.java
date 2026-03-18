package com.loopers.infrastructure.payment.pg;

/**
 * PG 결제 요청 응답 (POST /api/v1/payments 의 data 필드)
 */
public record PgTransactionResponse(
    String transactionKey,
    String status,
    String reason
) {
}
