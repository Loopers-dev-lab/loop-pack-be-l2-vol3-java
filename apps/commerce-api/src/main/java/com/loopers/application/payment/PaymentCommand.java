package com.loopers.application.payment;

public record PaymentCommand(
    Long userId,
    Long orderId,
    String cardType,
    String cardNo
) {
}
