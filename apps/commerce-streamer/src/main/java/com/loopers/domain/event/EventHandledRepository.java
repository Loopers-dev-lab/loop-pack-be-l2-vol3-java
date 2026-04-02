package com.loopers.domain.event;

import java.time.ZonedDateTime;

public interface EventHandledRepository {

    boolean existsByEventIdAndEventType(String eventId, String eventType);

    boolean existsByEntityIdAndEventTypeAndOccurredAtGreaterThanEqual(String entityId, String eventType, ZonedDateTime occurredAt);

    EventHandled save(EventHandled eventHandled);
}
