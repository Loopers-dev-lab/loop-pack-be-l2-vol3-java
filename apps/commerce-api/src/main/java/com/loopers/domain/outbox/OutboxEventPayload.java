package com.loopers.domain.outbox;

import java.time.ZonedDateTime;
import java.util.Map;

public record OutboxEventPayload(
    String eventId,
    String eventType,
    ZonedDateTime occurredAt,
    Map<String, Object> data
) {}
