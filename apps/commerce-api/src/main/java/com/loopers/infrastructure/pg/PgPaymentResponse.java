package com.loopers.infrastructure.pg;

/**
 * PG 결제 요청에 대한 응답 DTO.
 * PG 시뮬레이터: status=PENDING + transactionKey 반환.
 * Toss Sandbox: status=SUCCESS/FAILED 즉시 반환.
 *
 * <p>pgProvider는 PgRouter에서 주입한다 (PG Feign 응답에는 없음).</p>
 */
public record PgPaymentResponse(
    String status,
    String transactionKey,
    String pgProvider
) {
    /**
     * pgProvider 없이 생성 (Feign 역직렬화, 테스트용).
     */
    public PgPaymentResponse(String status, String transactionKey) {
        this(status, transactionKey, null);
    }
}
