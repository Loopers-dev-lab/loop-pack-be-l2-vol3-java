package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;

public record CreatePaymentResult(
        Long paymentId,
        String transactionKey,
        PaymentStatus status
) {

    public static CreatePaymentResult from(Payment payment) {
        return new CreatePaymentResult(
                payment.getId(),
                payment.getTransactionKey(),
                payment.getStatus()
        );
    }
}
