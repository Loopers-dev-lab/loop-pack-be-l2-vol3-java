package com.loopers.domain.payment.event;

public record PaymentCompletedEvent(
    Long paymentId,
    Long orderId,
    Long userId,
    boolean success
) {}
