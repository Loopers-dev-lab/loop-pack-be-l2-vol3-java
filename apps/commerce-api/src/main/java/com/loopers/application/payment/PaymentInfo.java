package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;

import java.time.ZonedDateTime;

public record PaymentInfo(
        Long paymentId,
        Long orderId,
        String transactionKey,
        String status,
        int amount,
        String cardType,
        String cardNo,
        String failureReason,
        ZonedDateTime pgRespondedAt,
        ZonedDateTime createdAt
) {

    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getTransactionKey(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getCardType(),
                payment.getCardNo(),
                payment.getFailureReason(),
                payment.getPgRespondedAt(),
                payment.getCreatedAt()
        );
    }
}
