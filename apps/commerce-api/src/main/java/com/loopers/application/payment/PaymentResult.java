package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import java.time.ZonedDateTime;

public record PaymentResult(
        Long paymentId,
        Long orderId,
        int amount,
        String status,
        String cardType,
        String maskedCardNo,
        ZonedDateTime createdAt) {

    public static PaymentResult from(PaymentModel payment) {
        return new PaymentResult(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getCardType().name(),
                payment.getMaskedCardNo(),
                payment.getCreatedAt());
    }
}
