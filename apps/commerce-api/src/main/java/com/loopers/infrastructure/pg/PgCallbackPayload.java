package com.loopers.infrastructure.pg;

/**
 * PG 콜백 수신 DTO.
 * PG 비동기 처리 완료 후 POST callback으로 전달되는 결과.
 */
public record PgCallbackPayload(
    String transactionKey,
    String orderId,
    String status,
    String reason
) {}
