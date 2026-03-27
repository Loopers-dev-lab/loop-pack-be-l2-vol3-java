package com.loopers.domain.payment;

public enum PgRequestStatus {

    ACCEPTED,
    SERVER_ERROR,
    VALIDATION_ERROR,
    CONNECTION_ERROR;

    public boolean isAccepted() {
        return this == ACCEPTED;
    }
}
