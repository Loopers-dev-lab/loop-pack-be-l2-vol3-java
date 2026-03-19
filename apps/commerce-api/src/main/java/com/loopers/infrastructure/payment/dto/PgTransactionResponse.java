package com.loopers.infrastructure.payment.dto;

public record PgTransactionResponse(
        PgMeta meta,
        PgTransactionData data
) {

    public boolean isSuccess() {
        return meta != null && "SUCCESS".equals(meta.result());
    }

    public record PgMeta(String result, String message) {
    }

    public record PgTransactionData(
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
