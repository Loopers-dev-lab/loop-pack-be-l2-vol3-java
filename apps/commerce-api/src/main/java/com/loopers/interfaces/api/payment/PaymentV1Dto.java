package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.domain.payment.PaymentModel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PaymentV1Dto {

    public record PaymentRequest(
        @NotNull Long orderId,
        @NotBlank String cardType,
        @NotBlank String cardNo,
        @Min(1) int amount
    ) {}

    public record PaymentResponse(
        Long paymentId,
        String transactionKey,
        String status,
        String failureReason
    ) {
        public static PaymentResponse from(PaymentFacade.PaymentResult result) {
            return new PaymentResponse(
                result.paymentId(),
                result.transactionKey(),
                result.status(),
                result.failureReason()
            );
        }
    }

    public record PaymentDetailResponse(
        Long paymentId,
        Long orderId,
        String status,
        int amount,
        String cardType,
        String pgProvider,
        String transactionKey,
        String failureReason
    ) {
        public static PaymentDetailResponse from(PaymentModel payment) {
            return new PaymentDetailResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getCardType(),
                payment.getPgProvider(),
                payment.getTransactionKey(),
                payment.getFailureReason()
            );
        }
    }
}
