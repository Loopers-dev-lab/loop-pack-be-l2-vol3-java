package com.loopers.domain.outbox;

import java.time.ZonedDateTime;
import java.util.UUID;

public record OutboxEvent(
        Long id,
        UUID eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String topic,
        String partitionKey,
        String payloadJson,
        OutboxEventStatus status,
        int attemptCount,
        ZonedDateTime nextAttemptAt,
        ZonedDateTime occurredAt,
        ZonedDateTime publishedAt,
        ZonedDateTime ackedAt
) {

    public static OutboxEvent pending(
            UUID eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            String topic,
            String partitionKey,
            String payloadJson,
            ZonedDateTime occurredAt
    ) {
        return new OutboxEvent(
                null,
                eventId,
                eventType,
                aggregateType,
                aggregateId,
                topic,
                partitionKey,
                payloadJson,
                OutboxEventStatus.PENDING,
                0,
                occurredAt,
                occurredAt,
                null,
                null
        );
    }
}
