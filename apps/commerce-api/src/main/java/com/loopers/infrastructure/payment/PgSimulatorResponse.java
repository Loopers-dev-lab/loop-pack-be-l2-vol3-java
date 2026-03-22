package com.loopers.infrastructure.payment;

/**
 * PG-Simulator 결제 접수 응답 (06 §2.2: "접수됨", transactionId 반환).
 * Phase 2 이후 실제 스펙에 맞게 필드 보강.
 */
public record PgSimulatorResponse(
        String transactionId
) {
}
