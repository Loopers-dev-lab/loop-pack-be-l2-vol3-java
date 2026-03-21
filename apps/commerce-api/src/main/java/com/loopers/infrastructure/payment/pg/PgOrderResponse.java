package com.loopers.infrastructure.payment.pg;

import java.util.List;

/**
 * PG orderId 기반 결제 목록 조회 응답 (GET /api/v1/payments?orderId={orderId} 의 data 필드)
 */
public record PgOrderResponse(
    String orderId,
    List<PgTransactionResponse> transactions
) {
}
