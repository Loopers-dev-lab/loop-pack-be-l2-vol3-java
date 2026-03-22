package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.PaymentRequest;

public record PgPaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
) {

    public static PgPaymentRequest from(PaymentRequest request) {
        return new PgPaymentRequest(
                request.orderId(),
                request.cardType(),
                request.cardNo(),
                request.amount(),
                request.callbackUrl()
        );
    }
}
