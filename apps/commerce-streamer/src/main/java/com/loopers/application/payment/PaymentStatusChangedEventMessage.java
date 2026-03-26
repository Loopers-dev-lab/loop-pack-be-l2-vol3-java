package com.loopers.application.payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentStatusChangedEventMessage(
        UUID eventId,
        UUID orderId,
        String memberId,
        String beforeStatus,
        String afterStatus,
        Instant occurredAt
) {
}
