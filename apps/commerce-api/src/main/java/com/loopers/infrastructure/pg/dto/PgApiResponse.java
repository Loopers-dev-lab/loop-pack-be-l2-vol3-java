package com.loopers.infrastructure.pg.dto;

public record PgApiResponse<T>(
        PgMeta meta,
        T data
) {
    public record PgMeta(
            String result,
            String errorCode,
            String message
    ) {}
}
