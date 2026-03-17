package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentStatus;

public record PaymentCallbackCommand(
        String transactionKey,
        PaymentStatus status,
        String reason
) {
}
