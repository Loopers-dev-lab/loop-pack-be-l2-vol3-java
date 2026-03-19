package com.loopers.infrastructure.client;

public class PgDeclinedException extends RuntimeException {
    public PgDeclinedException(String message) {
        super(message);
    }
}
