package com.loopers.domain.payment;

public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAIL;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAIL;
    }
}
