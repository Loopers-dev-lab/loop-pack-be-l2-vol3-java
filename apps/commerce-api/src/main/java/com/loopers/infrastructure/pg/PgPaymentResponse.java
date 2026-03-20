package com.loopers.infrastructure.pg;

/**
 * PG 결제 요청에 대한 응답 DTO.
 * PG 시뮬레이터: status=PENDING + transactionKey 반환.
 * Toss Sandbox: status=SUCCESS/FAILED 즉시 반환.
 */
public record PgPaymentResponse(
    String status,
    String transactionKey
) {}
