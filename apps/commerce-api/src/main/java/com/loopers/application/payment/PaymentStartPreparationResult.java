package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;

public record PaymentStartPreparationResult(
        Payment payment,
        boolean requiresGatewayRequest
) {
}
