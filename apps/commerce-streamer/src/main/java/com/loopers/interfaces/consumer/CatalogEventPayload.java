package com.loopers.interfaces.consumer;

public record CatalogEventPayload(
        String eventId,
        String eventType,
        int schemaVersion,
        Long productDbId,
        Long memberId,
        String occurredAt,
        int delta) {
}
