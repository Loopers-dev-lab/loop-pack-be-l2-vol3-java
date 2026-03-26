package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import java.time.ZonedDateTime;

public record PaymentInfo(
    Long paymentId,
    Long orderId,
    long amount,
    PaymentStatus status,
    ZonedDateTime createdAt
) {

    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(
            payment.getId(),
            payment.getOrderId(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getCreatedAt()
        );
    }
}