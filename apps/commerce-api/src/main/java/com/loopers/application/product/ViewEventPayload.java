package com.loopers.application.product;

import java.time.LocalDateTime;

public record ViewEventPayload(
        String eventId,
        String eventType,
        int schemaVersion,
        Long productDbId,
        Long memberId,
        int delta,
        LocalDateTime likedAt
) {
}
