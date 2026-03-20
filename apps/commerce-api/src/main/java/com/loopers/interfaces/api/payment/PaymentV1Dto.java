package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class PaymentV1Dto {

    public record PaymentRequest(
        @NotNull Long orderId,
        @NotNull Long memberId,
        @NotNull @Positive Long amount,
        @NotBlank String cardType,
        @NotBlank String cardNo
    ) {}

    public record PaymentResponse(
        Long paymentId,
        String pgTransactionKey,
        String status
    ) {
        public static PaymentResponse from(PaymentService.PaymentResult result) {
            return new PaymentResponse(result.paymentId(), result.pgTransactionKey(), result.status());
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
    ) {}
}
