package com.loopers.infrastructure.payment.pg;

public class PgServerException extends RuntimeException {

    public PgServerException(String message) {
        super(message);
    }
}
