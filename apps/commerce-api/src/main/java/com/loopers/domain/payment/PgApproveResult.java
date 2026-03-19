package com.loopers.domain.payment;

/**
 * PG 결제 승인 응답 결과 (Domain Layer)
 */
public record PgApproveResult(
        Status status,
        String transactionKey,
        String reason
) {
    public enum Status {
        PENDING,    // PG가 접수함 — 비동기 처리 대기
        REJECTED,   // PG가 명확히 거절 (400 에러 등)
        ERROR,      // PG 서버 에러 (500)
        TIMEOUT     // 응답 없음 — 결제 여부 불확실
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    public boolean isTimeout() {
        return status == Status.TIMEOUT;
    }

    public boolean isRejected() {
        return status == Status.REJECTED;
    }

    public static PgApproveResult pending(String transactionKey) {
        return new PgApproveResult(Status.PENDING, transactionKey, null);
    }

    public static PgApproveResult rejected(String reason) {
        return new PgApproveResult(Status.REJECTED, null, reason);
    }

    public static PgApproveResult error(String reason) {
        return new PgApproveResult(Status.ERROR, null, reason);
    }

    public static PgApproveResult timeout() {
        return new PgApproveResult(Status.TIMEOUT, null, "PG 응답 타임아웃");
    }
}
