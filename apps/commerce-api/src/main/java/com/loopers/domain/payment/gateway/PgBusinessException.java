package com.loopers.domain.payment.gateway;

public class PgBusinessException extends PgException {

    public PgBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
