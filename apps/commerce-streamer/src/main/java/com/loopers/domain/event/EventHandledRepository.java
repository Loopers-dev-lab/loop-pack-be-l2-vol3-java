package com.loopers.domain.event;

import java.time.ZonedDateTime;

public interface EventHandledRepository {
    EventHandled save(EventHandled eventHandled);
    boolean existsByEventId(String eventId);
    int insertIgnore(String eventId, ZonedDateTime occurredAt);
}
