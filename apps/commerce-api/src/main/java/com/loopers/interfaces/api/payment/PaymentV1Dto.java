package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentDto;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public class PaymentV1Dto {

    public record CreateRequest(
        @NotNull(message = "주문 ID는 필수입니다")
        Long orderId,

        @NotBlank(message = "카드 종류는 필수입니다")
        String cardType,

        @NotBlank(message = "카드 번호는 필수입니다")
        String cardNo
    ) {
        public PaymentDto.PayCommand toCommand() {
            return new PaymentDto.PayCommand(orderId, cardType, cardNo);
        }
    }

    public record CallbackRequest(
        String transactionKey,
        String orderId,
        String status
    ) {
        public PaymentDto.CallbackCommand toCommand() {
            return new PaymentDto.CallbackCommand(transactionKey, orderId, status);
        }
    }

    public record PaymentResponse(
        Long id,
        Long orderId,
        String pgTransactionId,
        String cardType,
        String cardNo,
        BigDecimal amount,
        PaymentStatus status,
        ZonedDateTime createdAt
    ) {
        public static PaymentResponse from(PaymentDto.PaymentInfo info) {
            return new PaymentResponse(
                info.id(),
                info.orderId(),
                info.pgTransactionId(),
                info.cardType(),
                info.cardNo(),
                info.amount(),
                info.status(),
                info.createdAt()
            );
        }
    }
}
