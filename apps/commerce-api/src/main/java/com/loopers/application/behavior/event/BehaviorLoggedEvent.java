package com.loopers.application.behavior.event;

import java.time.Instant;
import java.util.UUID;

public record BehaviorLoggedEvent(
        UUID eventId,
        BehaviorActionType actionType,
        String memberId,
        String productId,
        String orderId,
        long quantity,
        long version,
        Instant occurredAt
) {
}
