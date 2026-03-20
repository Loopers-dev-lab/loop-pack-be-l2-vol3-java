package com.loopers.domain.payment;

public class PaymentCommand {

    public record Create(
            String orderId,
            Long memberId,
            String cardType,
            String cardNo,
            String amount
    ) {
    }

    public record PgRequest(
            String orderId,
            String cardType,
            String cardNo,
            String amount,
            String callbackUrl
    ) {
    }
}
