package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentInfo(
        Long id,
        Long orderId,
        String transactionKey,
        CardType cardType,
        String cardNo,
        BigDecimal amount,
        PaymentStatus status,
        String failReason,
        LocalDateTime createdAt
) {

    public static PaymentInfo empty(Long orderId) {
        return new PaymentInfo(null, orderId, null, null, null, null, null, null, null);
    }

    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getTransactionKey(),
                payment.getCardType(),
                payment.getCardNo(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getFailReason(),
                payment.getCreatedAt().toLocalDateTime()
        );
    }
}
