package com.loopers.infrastructure.payment;

public class PgConnectionTimeoutException extends RuntimeException {

    public PgConnectionTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
