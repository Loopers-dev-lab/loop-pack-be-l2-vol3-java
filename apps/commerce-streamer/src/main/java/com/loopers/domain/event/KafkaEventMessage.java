package com.loopers.domain.event;

import java.time.LocalDateTime;
import java.util.Map;

public record KafkaEventMessage(
        String eventId,
        String eventType,
        String aggregateType,
        Long aggregateId,
        LocalDateTime timestamp,
        Map<String, Object> payload
) {}
