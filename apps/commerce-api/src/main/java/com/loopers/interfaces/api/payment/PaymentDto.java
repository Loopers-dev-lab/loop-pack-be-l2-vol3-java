package com.loopers.interfaces.api.payment;

public class PaymentDto {

    public static record PayRequest(
            String orderId,
            String cardType,
            String cardNo
    ){}

    public static record PaymentResponse(
            String orderId,
            String status,
            String reason
    ){}

    public static record CallbackRequest(
            String transactionKey,
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String status,
            String reason
    ){}
}
