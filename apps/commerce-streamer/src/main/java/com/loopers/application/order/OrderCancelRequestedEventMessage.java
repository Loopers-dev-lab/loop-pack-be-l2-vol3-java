package com.loopers.application.order;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelRequestedEventMessage(
        UUID eventId,
        UUID orderId,
        String memberId,
        Instant occurredAt
) {
}
