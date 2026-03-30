package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.application.payment.PgPaymentStatus;
import com.loopers.domain.payment.CardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public class PaymentV1Dto {

    public record PayRequest(
        @NotNull Long orderId,
        @NotNull CardType cardType,
        @NotBlank String cardNo
    ) {}

    public record PaymentResponse(
        Long id,
        Long orderId,
        Long amount,
        String cardType,
        String status,
        String orderStatus,
        String pgPaymentKey,
        String lastError,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                info.id(),
                info.orderId(),
                info.amount(),
                info.cardType(),
                info.status().name(),
                info.orderStatus().name(),
                info.pgPaymentKey(),
                info.lastError(),
                info.createdAt(),
                info.updatedAt()
            );
        }
    }

    public record CallbackRequest(
        Long orderId,
        String paymentKey,
        String transactionKey,
        PgPaymentStatus status,
        String reason,
        String message
    ) {
        public String resolvedPaymentKey() {
            if (paymentKey != null && !paymentKey.isBlank()) {
                return paymentKey;
            }
            return transactionKey;
        }

        public String resolvedReason() {
            if (reason != null && !reason.isBlank()) {
                return reason;
            }
            return message;
        }
    }
}
