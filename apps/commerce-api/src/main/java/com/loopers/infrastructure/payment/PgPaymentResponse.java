package com.loopers.infrastructure.payment;

public record PgPaymentResponse(
        Meta meta,
        Data data
) {
    public record Meta(String result, String errorCode, String message) {
    }

    public record Data(String transactionKey, String status) {
    }
}
