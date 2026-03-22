package com.loopers.infrastructure.payment;

/**
 * PG-Simulator 결제 조회 응답 (06-payment-change-issues §5.2).
 * GET /api/v1/payments/{paymentId}, GET /api/v1/payments?orderId= 에서 사용.
 * Phase 8 폴링·복구 시 파싱용. PG 스펙에 맞게 필드 확장 가능.
 */
public record PgPaymentStatusResponse(
        String paymentId,
        Long orderId,
        Boolean success,
        String status,
        Long amount,
        String failureReason
) {
    /** PG가 success를 생략하면 null — 자동 언박싱 NPE 방지. */
    public boolean isSuccessful() {
        return Boolean.TRUE.equals(success);
    }
}
