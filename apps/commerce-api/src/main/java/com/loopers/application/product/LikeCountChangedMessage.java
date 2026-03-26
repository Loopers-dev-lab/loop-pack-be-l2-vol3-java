package com.loopers.application.product;

import java.time.Instant;
import java.util.UUID;

public record LikeCountChangedMessage(
        UUID eventId,
        String actionType,
        String memberId,
        UUID productId,
        int delta,
        Instant occurredAt
) {
}
