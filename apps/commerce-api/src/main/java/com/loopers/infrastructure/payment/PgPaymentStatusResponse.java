package com.loopers.infrastructure.payment;

public record PgPaymentStatusResponse(
        Meta meta,
        Data data
) {
    public record Meta(String result, String errorCode, String message) {
    }

    public record Data(
            String transactionKey,
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String status,
            String reason
    ) {
    }
}
