package com.loopers.infrastructure.client;

public class PgPaymentException extends RuntimeException {
    public PgPaymentException(String message) {
        super(message);
    }
}
