package com.loopers.domain.payment;

public record PgResult(String pgTransactionKey, PgStatus status, String reason, java.math.BigDecimal amount) {

    public static PgResult unavailable() {
        return new PgResult(null, PgStatus.UNAVAILABLE, null, null);
    }

    public boolean isAccepted() {
        return status == PgStatus.PENDING;
    }

    public boolean isSuccess() {
        return status == PgStatus.SUCCESS;
    }

    public boolean isUnavailable() {
        return status == PgStatus.UNAVAILABLE;
    }
}
