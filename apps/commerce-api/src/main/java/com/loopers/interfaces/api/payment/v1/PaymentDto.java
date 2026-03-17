package com.loopers.interfaces.api.payment.v1;

import jakarta.validation.constraints.NotNull;

import com.loopers.application.payment.CreatePaymentCommand;
import com.loopers.application.payment.CreatePaymentResult;
import com.loopers.domain.payment.PaymentStatus;

public class PaymentDto {

    public record CreatePaymentRequest(
            @NotNull(message = "주문 키는 필수입니다.") String orderKey,
            @NotNull(message = "카드 종류는 필수입니다.") String cardType,
            @NotNull(message = "카드 번호는 필수입니다.") String cardNo,
            @NotNull(message = "콜백 URL은 필수입니다.") String callbackUrl
    ) {

        public CreatePaymentCommand toCommand(Long userId) {
            return new CreatePaymentCommand(userId, orderKey, cardType, cardNo, callbackUrl);
        }
    }

    public record CreatePaymentResponse(
            Long paymentId,
            String transactionKey,
            PaymentStatus status
    ) {

        public static CreatePaymentResponse from(CreatePaymentResult result) {
            return new CreatePaymentResponse(
                    result.paymentId(),
                    result.transactionKey(),
                    result.status()
            );
        }
    }
}
