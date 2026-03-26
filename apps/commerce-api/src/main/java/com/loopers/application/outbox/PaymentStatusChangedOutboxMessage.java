package com.loopers.application.outbox;

import com.loopers.domain.payment.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record PaymentStatusChangedOutboxMessage(
        UUID eventId,
        UUID orderId,
        String memberId,
        PaymentStatus beforeStatus,
        PaymentStatus afterStatus,
        Instant occurredAt
) {
}
