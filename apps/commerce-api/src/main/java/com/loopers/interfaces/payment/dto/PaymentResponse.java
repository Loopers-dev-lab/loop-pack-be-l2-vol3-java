package com.loopers.interfaces.payment.dto;

import com.loopers.application.payment.PaymentResult;
import com.loopers.application.payment.PaymentStatusResult;
import java.time.ZonedDateTime;

public class PaymentResponse {

    public record PaymentSummary(
            Long paymentId,
            Long orderId,
            int amount,
            String status,
            String cardType,
            String maskedCardNo,
            ZonedDateTime createdAt
    ) {
        public static PaymentSummary from(PaymentResult result) {
            return new PaymentSummary(
                    result.paymentId(),
                    result.orderId(),
                    result.amount(),
                    result.status(),
                    result.cardType(),
                    result.maskedCardNo(),
                    result.createdAt());
        }
    }

    public record PaymentStatus(
            String paymentStatus,
            String failureCode,
            String failureMessage
    ) {
        public static PaymentStatus from(PaymentStatusResult result) {
            return new PaymentStatus(
                    result.paymentStatus(),
                    result.failureCode(),
                    result.failureMessage());
        }
    }
}
