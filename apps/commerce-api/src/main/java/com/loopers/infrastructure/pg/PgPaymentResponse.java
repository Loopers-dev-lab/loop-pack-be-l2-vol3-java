package com.loopers.infrastructure.pg;

/** PG 결제 요청(POST) 응답 - data.transactionKey */
public record PgPaymentResponse(
    String transactionKey,
    String status,
    String reason
) {
}
