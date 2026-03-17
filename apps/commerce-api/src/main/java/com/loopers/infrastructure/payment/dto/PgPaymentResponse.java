package com.loopers.infrastructure.payment.dto;

public record PgPaymentResponse(
        PgMeta meta,
        PgPaymentData data
) {

    public boolean isSuccess() {
        return meta != null && "SUCCESS".equals(meta.result());
    }

    public record PgMeta(String result, String message) {
    }

    public record PgPaymentData(
            String transactionKey,
            String status,
            String reason
    ) {
    }
}
