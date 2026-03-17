package com.loopers.infrastructure.client;

public class PgPaymentDto {

    public record PaymentRequest(
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String callbackUrl
    ) {}

    public record TransactionResponse(
            String transactionKey,
            String status,
            String reason
    ) {}

    public record TransactionDetailResponse(
            String transactionKey,
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String status,
            String reason
    ) {}
}
