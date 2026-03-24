package com.loopers.application.payment.pg.exception;

public abstract class PgException extends RuntimeException {
    protected PgException(String message) {
        super(message);
    }
}
