package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;

import java.time.ZonedDateTime;

public class PaymentV1Dto {

    public record PaymentRequest(
        Long orderId,
        String cardType,
        String cardNo
    ) {
        public PaymentCommand toCommand() {
            return new PaymentCommand(orderId, CardType.valueOf(cardType), cardNo);
        }
    }

    public record PaymentResponse(
        Long paymentId,
        Long orderId,
        String transactionKey,
        String cardType,
        String maskedCardNo,
        int amount,
        String status,
        String failureReason,
        ZonedDateTime createdAt
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                info.paymentId(),
                info.orderId(),
                info.transactionKey(),
                info.cardType(),
                info.maskedCardNo(),
                info.amount(),
                info.status(),
                info.failureReason(),
                info.createdAt()
            );
        }
    }

    public record CallbackRequest(
        String transactionKey,
        String orderId,
        String status,
        String failureReason
    ) {}
}
