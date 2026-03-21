package com.loopers.infrastructure.payment.pg;

/**
 * PG 시뮬레이터의 공통 응답 래퍼.
 * { "meta": { "result": "SUCCESS", ... }, "data": { ... } }
 */
public record PgApiResponse<T>(
    Metadata meta,
    T data
) {

    public record Metadata(
        String result,
        String errorCode,
        String message
    ) {}

    public boolean isSuccess() {
        return "SUCCESS".equals(meta.result());
    }
}
