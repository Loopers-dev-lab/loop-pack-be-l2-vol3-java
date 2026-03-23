package com.loopers.interfaces.api.payment.dto;

import com.loopers.application.payment.dto.CreatePaymentReqDto;

public record CreatePaymentApiReqDto(
        String orderId,
        String cardType,
        String cardNo
) {
    public CreatePaymentReqDto toDto() {
        return new CreatePaymentReqDto(orderId, cardType, cardNo);
    }
}
