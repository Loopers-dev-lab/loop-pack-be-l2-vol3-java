package com.loopers.domain.payment;

public enum PgCallbackStatus {

    APPROVED,
    LIMIT_EXCEEDED,
    INVALID_CARD,
    PG_ERROR;

    public boolean isSuccess() {
        return this == APPROVED;
    }

    public static PgCallbackStatus from(String pgStatus, String reason) {
        if ("SUCCESS".equals(pgStatus)) {
            return APPROVED;
        }
        if (reason != null && reason.contains("한도초과")) {
            return LIMIT_EXCEEDED;
        }
        if (reason != null && reason.contains("잘못된 카드")) {
            return INVALID_CARD;
        }
        return PG_ERROR;
    }
}
