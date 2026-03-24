package com.loopers.application.payment.pg.exception;

public class PgBadRequestException extends PgException {
    public PgBadRequestException(String message) {
        super(message);
    }
}
