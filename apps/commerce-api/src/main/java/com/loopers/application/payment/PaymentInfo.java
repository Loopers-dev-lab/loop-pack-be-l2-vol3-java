package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;

import java.time.ZonedDateTime;

public record PaymentInfo(
    String orderId,
    String cardType,
    String cardNo,
    Long amount,
    PaymentStatus status,
    String reason,
    String transactionKey,
    ZonedDateTime createdAt
) {
    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(
                payment.orderId(),
                payment.cardType(),
                payment.cardNo(),
                payment.amount(),
                payment.status(),
                payment.reason(),
                payment.transactionKey(),
                payment.getCreatedAt());
    }
}
