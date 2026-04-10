package com.loopers.confg.kafka;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

public record KafkaEventMessage(
        String eventId,
        String eventType,
        ZonedDateTime occurredAt,
        Map<String, Object> data
) {
    public static KafkaEventMessage of(String eventType, Map<String, Object> data) {
        return new KafkaEventMessage(
                UUID.randomUUID().toString(),
                eventType,
                ZonedDateTime.now(),
                data);
    }
}
