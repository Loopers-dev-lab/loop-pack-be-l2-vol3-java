package com.loopers.infrastructure.payment.nice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NicePaymentResponse(
        String resultCode,
        String resultMsg,
        String tid,
        String orderId,
        String status,
        Long amount,
        Long balanceAmt,
        String paidAt,
        String cancelledAt
) {

    public boolean isPaid() {
        return "paid".equals(status);
    }

    public boolean isSuccess() {
        return "0000".equals(resultCode);
    }
}
