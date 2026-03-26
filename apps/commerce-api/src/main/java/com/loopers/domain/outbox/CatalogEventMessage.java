package com.loopers.domain.outbox;

import java.time.ZonedDateTime;

public record CatalogEventMessage(
        String eventId,
        String eventType,
        String aggregateId,
        String payload,
        ZonedDateTime occurredAt
) {
    public static CatalogEventMessage from(OutboxEvent outboxEvent) {
        return new CatalogEventMessage(
                outboxEvent.getEventId(),
                outboxEvent.getEventType(),
                outboxEvent.getAggregateId(),
                outboxEvent.getPayload(),
                outboxEvent.getOccurredAt()
        );
    }
}
