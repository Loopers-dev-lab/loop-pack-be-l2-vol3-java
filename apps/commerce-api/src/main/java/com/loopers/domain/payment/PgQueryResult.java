package com.loopers.domain.payment;

/**
 * PG 결제 상태 조회 결과 (Domain Layer)
 */
public record PgQueryResult(
        Status status,
        String transactionKey,
        String reason
) {
    public enum Status {
        SUCCESS,    // PG 승인 완료
        PENDING,    // 아직 처리 중
        FAILED,     // PG 거절 (한도초과, 잘못된 카드 등)
        NOT_FOUND,  // 해당 건이 PG에 없음
        ERROR       // 조회 자체 실패 (네트워크 등)
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean isNotFound() {
        return status == Status.NOT_FOUND;
    }

    public static PgQueryResult success(String transactionKey, String reason) {
        return new PgQueryResult(Status.SUCCESS, transactionKey, reason);
    }

    public static PgQueryResult pending(String transactionKey) {
        return new PgQueryResult(Status.PENDING, transactionKey, null);
    }

    public static PgQueryResult failed(String transactionKey, String reason) {
        return new PgQueryResult(Status.FAILED, transactionKey, reason);
    }

    public static PgQueryResult notFound() {
        return new PgQueryResult(Status.NOT_FOUND, null, null);
    }

    public static PgQueryResult error(String reason) {
        return new PgQueryResult(Status.ERROR, null, reason);
    }
}
