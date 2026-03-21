package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;

import java.math.BigDecimal;

public record PaymentDetailResponse(
        Long id,
        Long orderId,
        Long memberId,
        CardType cardType,
        String cardNo,
        BigDecimal amount,
        PaymentStatus status,
        String pgTransactionId
) {
    public static PaymentDetailResponse from(PaymentInfo info) {
        return new PaymentDetailResponse(
                info.id(),
                info.orderId(),
                info.memberId(),
                info.cardType(),
                info.cardNo(),
                info.amount(),
                info.status(),
                info.pgTransactionId()
        );
    }
}
