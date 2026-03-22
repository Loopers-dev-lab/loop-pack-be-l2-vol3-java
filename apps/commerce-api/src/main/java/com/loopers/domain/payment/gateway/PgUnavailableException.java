package com.loopers.domain.payment.gateway;

public class PgUnavailableException extends PgException {

    public PgUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
