package com.loopers.domain.payment;

/**
 * PG 결제 취소 결과 (Domain Layer)
 */
public record PgCancelResult(
        boolean canceled,
        String reason
) {
    public static PgCancelResult ofSuccess() {
        return new PgCancelResult(true, null);
    }

    public static PgCancelResult ofFailure(String reason) {
        return new PgCancelResult(false, reason);
    }
}
