package com.loopers.contract.kafka;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelRequestedOutboxMessage(
        UUID eventId,
        UUID orderId,
        String memberId,
        Instant occurredAt
) {
}
