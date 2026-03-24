package com.loopers.application.payment.pg.exception;

public class PgCircuitOpenException extends PgException {
    public PgCircuitOpenException(String message) {
        super(message);
    }
}
