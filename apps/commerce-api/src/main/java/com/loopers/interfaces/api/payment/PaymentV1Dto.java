package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentV1Dto {

    // Response

    public record PaymentResponse(
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

        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                    info.id(),
                    info.orderId(),
                    info.transactionKey(),
                    info.cardType(),
                    info.cardNo(),
                    info.amount(),
                    info.status(),
                    info.failReason(),
                    info.createdAt()
            );
        }
    }
}
