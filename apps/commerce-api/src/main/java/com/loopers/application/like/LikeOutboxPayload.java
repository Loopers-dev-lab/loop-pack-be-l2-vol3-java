package com.loopers.application.like;

import java.time.LocalDateTime;

public record LikeOutboxPayload(
        String eventType,
        int schemaVersion,
        Long productDbId,
        Long memberId,
        LocalDateTime occurredAt,
        int delta) {
}
