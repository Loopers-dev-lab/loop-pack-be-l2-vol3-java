package com.loopers.application.payment.dto;

public record CreatePaymentReqDto(
        String orderId,
        String cardType,
        String cardNo
) {
}
