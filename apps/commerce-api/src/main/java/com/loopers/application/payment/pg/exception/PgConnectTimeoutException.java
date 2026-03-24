package com.loopers.application.payment.pg.exception;

public class PgConnectTimeoutException extends PgException {
    public PgConnectTimeoutException(String message) {
        super(message);
    }
}
