package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;

import java.time.ZonedDateTime;

/**
 * 결제 결과 DTO.
 */
public record PaymentResult(
        Long paymentId,
        Long orderId,
        String transactionId,
        Long amount,
        PaymentStatus status,
        String failReason,
        String errorMessage,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static PaymentResult from(Payment payment) {
        return new PaymentResult(
                payment.getId(),
                payment.getOrderId(),
                payment.getTransactionId(),
                payment.getAmount().amount(),
                payment.getStatus(),
                payment.getFailReason(),
                null,
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

    /**
     * Fallback 시 에러 메시지를 포함한 결과 생성.
     */
    public static PaymentResult fallback(Payment payment, String errorMessage) {
        return new PaymentResult(
                payment.getId(),
                payment.getOrderId(),
                payment.getTransactionId(),
                payment.getAmount().amount(),
                payment.getStatus(),
                payment.getFailReason(),
                errorMessage,
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

    /**
     * 결제 정보 없이 에러만 반환하는 경우.
     */
    public static PaymentResult error(Long orderId, String errorMessage) {
        return new PaymentResult(
                null,
                orderId,
                null,
                null,
                null,
                null,
                errorMessage,
                null,
                null
        );
    }

    public boolean hasError() {
        return errorMessage != null;
    }
}
