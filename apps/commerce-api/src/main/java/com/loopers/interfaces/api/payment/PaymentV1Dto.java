package com.loopers.interfaces.api.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public class PaymentV1Dto {

    public record PaymentRequest(
        @NotNull(message = "주문 ID는 필수입니다.")
        Long orderId,

        @NotNull(message = "카드 종류는 필수입니다.")
        CardType cardType,

        @NotBlank(message = "카드 번호는 필수입니다.")
        String cardNo
    ) {}

    public record PaymentResponse(
        Long paymentId,
        Long orderId,
        String cardType,
        String cardNo,
        int amount,
        String status,
        String transactionKey,
        String failureReason,
        ZonedDateTime createdAt
    ) {
        public static PaymentResponse from(Payment payment) {
            return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getCardType().name(),
                maskCardNo(payment.getCardNo()),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getTransactionKey(),
                payment.getFailureReason(),
                payment.getCreatedAt()
            );
        }

        private static String maskCardNo(String cardNo) {
            if (cardNo == null || cardNo.length() < 4) {
                return "****";
            }
            return "****-****-****-" + cardNo.substring(cardNo.length() - 4);
        }
    }

    public record PaymentCallbackRequest(
        @NotBlank(message = "트랜잭션 키는 필수입니다.")
        String transactionKey,

        @NotBlank(message = "결제 상태는 필수입니다.")
        String status,

        String reason
    ) {}
}
