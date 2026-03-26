package com.loopers.application.payment;

public enum PgPaymentStatus {
    REQUESTED,
    PROCESSING,
    SUCCESS,
    LIMIT_EXCEEDED,
    INVALID_CARD,
    FAILED,
    UNKNOWN
}
