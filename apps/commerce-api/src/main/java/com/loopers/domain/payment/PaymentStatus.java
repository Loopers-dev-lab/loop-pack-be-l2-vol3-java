package com.loopers.domain.payment;

public enum PaymentStatus {
    REQUESTED,
    SUCCEEDED,
    FAILED,
    CANCEL_REQUESTED,
    CANCEL_RECONCILE_REQUIRED,
    CANCELLED,
    CANCEL_FAILED
}
