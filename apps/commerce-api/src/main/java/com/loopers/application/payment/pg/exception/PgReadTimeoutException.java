package com.loopers.application.payment.pg.exception;

public class PgReadTimeoutException extends PgException {
    public PgReadTimeoutException(String message) {
        super(message);
    }
}
