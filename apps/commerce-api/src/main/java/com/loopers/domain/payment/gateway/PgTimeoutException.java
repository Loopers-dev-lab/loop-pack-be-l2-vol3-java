package com.loopers.domain.payment.gateway;

public class PgTimeoutException extends PgException {

    public PgTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
