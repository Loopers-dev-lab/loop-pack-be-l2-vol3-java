package com.loopers.application.outbox;

import java.time.Instant;
import java.util.UUID;

public record OrderCreatedOutboxMessage(
        UUID eventId,
        UUID orderId,
        String memberId,
        int totalAmount,
        Instant occurredAt
) {
}
