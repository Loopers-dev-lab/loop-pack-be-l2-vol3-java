package com.loopers.interfaces.api.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public class PaymentDto {

    public record StartPaymentRequest(
            @NotNull(message = "주문 ID는 필수입니다")
            UUID orderId,
            @NotNull(message = "카드 타입은 필수입니다")
            CardType cardType,
            @NotBlank(message = "카드 번호는 필수입니다")
            String cardNo
    ) {
    }

    public record PaymentResponse(
            UUID id,
            UUID orderId,
            String cardType,
            String cardNo,
            int amount,
            String status,
            String transactionKey,
            String reason,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static PaymentResponse from(Payment payment) {
            return new PaymentResponse(
                    payment.id(),
                    payment.orderId(),
                    payment.cardType().name(),
                    maskCardNo(payment.cardNo()),
                    payment.amount(),
                    payment.status().name(),
                    payment.pgTransactionKey(),
                    payment.reason(),
                    payment.createdAt(),
                    payment.updatedAt()
            );
        }

        private static String maskCardNo(String cardNo) {
            if (cardNo == null || cardNo.isBlank()) {
                return cardNo;
            }
            String[] parts = cardNo.split("-");
            if (parts.length != 4) {
                return "****-****-****-****";
            }
            return "****-****-****-" + parts[3];
        }
    }

    public record PaymentListResponse(
            UUID orderId,
            List<PaymentResponse> payments
    ) {
        public static PaymentListResponse from(UUID orderId, List<Payment> payments) {
            return new PaymentListResponse(
                    orderId,
                    payments.stream().map(PaymentResponse::from).toList()
            );
        }
    }
}
