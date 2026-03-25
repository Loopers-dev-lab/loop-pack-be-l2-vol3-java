package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.application.payment.PgCallbackCommand;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;

public class PaymentV1Dto {

    public record PaymentRequest(
            Long orderId,
            CardType cardType,
            String cardNo
    ) {
        public PaymentCommand toCommand() {
            return new PaymentCommand(orderId, cardType, cardNo);
        }
    }

    public record PaymentResponse(
            Long paymentId,
            Long orderId,
            PaymentStatus status,
            String failReason
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(info.id(), info.orderId(), info.status(), info.failReason());
        }
    }

    public record CallbackRequest(
            String transactionKey,
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String status,
            String reason
    ) {
        public PgCallbackCommand toCommand() {
            return new PgCallbackCommand(transactionKey, status, reason);
        }
    }
}
