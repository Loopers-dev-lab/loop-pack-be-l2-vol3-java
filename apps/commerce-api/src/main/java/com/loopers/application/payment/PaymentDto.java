package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class PaymentDto {

    public record PayCommand(
        Long orderId,
        String cardType,
        String cardNo
    ) {
    }

    public record CallbackCommand(
        String transactionId,
        String orderId,
        String status
    ) {
    }

    public record PaymentInfo(
        Long id,
        Long orderId,
        Long userId,
        String pgTransactionId,
        String cardType,
        String cardNo,
        BigDecimal amount,
        PaymentStatus status,
        ZonedDateTime createdAt
    ) {
        public static PaymentInfo from(Payment payment) {
            return new PaymentInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getPgTransactionId(),
                payment.getCardType(),
                payment.getCardNo(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCreatedAt()
            );
        }
    }
}
