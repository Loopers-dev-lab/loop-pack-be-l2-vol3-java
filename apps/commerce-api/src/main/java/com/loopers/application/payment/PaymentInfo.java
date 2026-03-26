package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record PaymentInfo(
        Long id,
        Long orderId,
        Long userId,
        BigDecimal amount,
        String cardType,
        String cardNo,
        String status,
        String transactionKey,
        String failureReason,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getCardType().name(),
                payment.getCardNo(),
                payment.getStatus().name(),
                payment.getTransactionKey(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
