package com.loopers.application.payment.event;

import com.loopers.domain.payment.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record PaymentStatusChangedEvent(
        String memberId,
        UUID orderId,
        PaymentStatus beforeStatus,
        PaymentStatus afterStatus,
        Instant changedAt
) {
    public PaymentStatusChangedEvent(
            String memberId,
            UUID orderId,
            PaymentStatus beforeStatus,
            PaymentStatus afterStatus
    ) {
        this(memberId, orderId, beforeStatus, afterStatus, Instant.now());
    }
}
