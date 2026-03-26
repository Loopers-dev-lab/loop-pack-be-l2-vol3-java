package com.loopers.application.order;

import java.time.Instant;
import java.util.UUID;

public record OrderCreatedEventMessage(
        UUID eventId,
        UUID orderId,
        String memberId,
        int totalAmount,
        Instant occurredAt
) {
}
