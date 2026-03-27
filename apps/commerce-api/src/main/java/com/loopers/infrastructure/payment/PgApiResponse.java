package com.loopers.infrastructure.payment;

public record PgApiResponse<T>(Meta meta, T data) {

    public record Meta(String result, String message) {
    }

    public boolean isSuccess() {
        return meta != null && "SUCCESS".equals(meta.result());
    }
}
