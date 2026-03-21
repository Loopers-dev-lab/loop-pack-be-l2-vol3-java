package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;

import java.math.BigDecimal;

public record PaymentInfo(
    Long id,
    Long orderId,
    Long memberId,
    CardType cardType,
    String cardNo,
    BigDecimal amount,
    PaymentStatus status,
    String pgTransactionId
) {
    public boolean isCompleted() {
        return status == PaymentStatus.COMPLETED;
    }

    public static PaymentInfo from(PaymentModel model) {
        return new PaymentInfo(
            model.getId(),
            model.getRefOrderId().value(),
            model.getRefMemberId(),
            model.getCardType(),
            model.getCardNumber(),
            model.getAmount(),
            model.getStatus(),
            model.getPgTransactionId()
        );
    }
}
