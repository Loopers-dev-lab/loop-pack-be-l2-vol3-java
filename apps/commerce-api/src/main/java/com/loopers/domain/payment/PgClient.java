package com.loopers.domain.payment;

public interface PgClient {

    PgPaymentResponse requestPayment(PgPaymentRequest request);

    record PgPaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
    ) {}

    record PgPaymentResponse(
        String transactionKey,
        String status,
        String reason
    ) {}
}
