package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.PaymentStatus;

import java.math.BigDecimal;

public record PaymentResponse(
        Long id,
        Long orderId,
        PaymentStatus status,
        BigDecimal amount
) {
    public static PaymentResponse from(PaymentInfo info) {
        return new PaymentResponse(info.id(), info.orderId(), info.status(), info.amount());
    }
}
