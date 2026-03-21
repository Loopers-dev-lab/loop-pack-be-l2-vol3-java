package com.loopers.infrastructure.payment.pg;

/**
 * PG 결제 상세 조회 응답 (GET /api/v1/payments/{transactionKey} 의 data 필드)
 */
public record PgTransactionDetailResponse(
    String transactionKey,
    String orderId,
    String cardType,
    String cardNo,
    long amount,
    String status,
    String reason
) {
}
