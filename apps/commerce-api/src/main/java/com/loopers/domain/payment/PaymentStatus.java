package com.loopers.domain.payment;

public enum PaymentStatus {
    REQUESTED,
    PENDING,
    SUCCESS,
    FAILED_LIMIT_EXCEEDED,
    FAILED_INVALID_CARD,
    FAILED
}
