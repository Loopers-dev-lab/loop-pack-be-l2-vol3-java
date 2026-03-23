package com.loopers.infrastructure.pg;

/** PG 트랜잭션 상세 조회(GET /{transactionKey}) 응답 */
public record PgPaymentInfo(
    String transactionKey,
    String orderId,
    String status,   // "SUCCESS" | "FAILED" | "PENDING"
    Long amount
) {
}
