package com.loopers.contract.kafka;

import java.time.Instant;
import java.util.UUID;

public record PaymentStatusChangedOutboxMessage(
        UUID eventId,
        UUID orderId,
        String memberId,
        String beforeStatus,
        String afterStatus,
        Instant occurredAt
) {
}
