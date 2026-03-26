package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class PaymentV1Dto {

    public record PaymentRequest(
            Long orderId,
            CardType cardType,
            String cardNo
    ) {
    }

    public record Response(
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
        public static Response from(PaymentInfo info) {
            return new Response(
                    info.id(),
                    info.orderId(),
                    info.userId(),
                    info.amount(),
                    info.cardType(),
                    info.cardNo(),
                    info.status(),
                    info.transactionKey(),
                    info.failureReason(),
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }

    public record CallbackRequest(
            Long orderId,
            String transactionKey,
            String status,
            String reason
    ) {
    }
}
