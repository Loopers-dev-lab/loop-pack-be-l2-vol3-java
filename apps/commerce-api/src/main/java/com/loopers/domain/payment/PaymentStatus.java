package com.loopers.domain.payment;

/**
 * 결제 상태 (06 §6.2).
 * PENDING(접수 대기) → SUCCESS / FAILED / TIMEOUT.
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    TIMEOUT
}
