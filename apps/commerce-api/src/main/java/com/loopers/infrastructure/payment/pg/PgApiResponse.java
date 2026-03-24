package com.loopers.infrastructure.payment.pg;

public record PgApiResponse<T>(
        Metadata meta,
        T data
) {
    public record Metadata(
            Result result,
            String errorCode,
            String message
    ) {}

    public enum Result {
        SUCCESS, FAIL
    }
}
