package com.loopers.infrastructure.pg;

/**
 * PG 결제 상태 확인 응답 DTO.
 * GET /api/v1/payments/{transactionKey} 또는 GET /api/v1/payments?orderId={orderId}
 */
public record PgPaymentStatusResponse(
    String status,
    String transactionKey,
    String reason
) {}
