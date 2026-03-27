package com.loopers.interfaces.consumer;

import java.time.LocalDateTime;

public record CatalogEventPayload(
        String eventId,
        String eventType,
        int schemaVersion,
        Long productDbId,
        Long memberId,
        int delta,
        LocalDateTime likedAt
) {
}
