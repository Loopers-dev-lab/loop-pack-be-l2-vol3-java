package com.loopers.domain.payment;

public record PgPaymentResult(
        boolean requested,
        String transactionKey,
        PgRequestStatus status,
        String pgDetail) {

    public PgPaymentResult(boolean requested, String transactionKey, PgRequestStatus status) {
        this(requested, transactionKey, status, null);
    }
}
