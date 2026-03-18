package com.loopers.infrastructure.client;

public enum PgTransactionStatus {
    SUCCESS, FAILED, UNKNOWN;

    public static PgTransactionStatus from(String raw) {
        for (PgTransactionStatus status : values()) {
            if (status.name().equals(raw)) {
                return status;
            }
        }

        return UNKNOWN;
    }

    public boolean hasResult() {
        return this == SUCCESS || this == FAILED;
    }
}
