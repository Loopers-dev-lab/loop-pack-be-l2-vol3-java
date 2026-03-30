package com.loopers.application.payment;

public record PgPaymentSnapshot(
    Long orderId,
    String paymentKey,
    PgPaymentStatus status,
    String reason
) {}
