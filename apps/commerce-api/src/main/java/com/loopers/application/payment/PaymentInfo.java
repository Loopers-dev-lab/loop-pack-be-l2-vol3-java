package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;

public record PaymentInfo(
        Long id,
        Long orderId,
        PaymentStatus status,
        String failReason
) {
    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getStatus(),
                payment.getFailReason()
        );
    }
}
