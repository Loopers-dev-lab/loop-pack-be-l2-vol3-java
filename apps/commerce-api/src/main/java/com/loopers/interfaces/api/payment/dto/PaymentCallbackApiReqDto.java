package com.loopers.interfaces.api.payment.dto;

public record PaymentCallbackApiReqDto(
        String transactionKey,
        String orderId,
        String status
) {
}
