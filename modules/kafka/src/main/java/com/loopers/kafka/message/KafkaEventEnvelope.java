package com.loopers.kafka.message;

import java.time.ZonedDateTime;
import java.util.Map;

public record KafkaEventEnvelope(
    String eventId,
    String eventType,
    String aggregateType,
    String aggregateId,
    String partitionKey,
    Integer version,
    ZonedDateTime occurredAt,
    Map<String, Object> payload
) {
}
