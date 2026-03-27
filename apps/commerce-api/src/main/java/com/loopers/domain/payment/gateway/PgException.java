package com.loopers.domain.payment.gateway;

public class PgException extends RuntimeException {

    public PgException(String message, Throwable cause) {
        super(message, cause);
    }
}
