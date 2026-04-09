package com.loopers.domain.event;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface EventHandledRepository {
    EventHandled save(EventHandled eventHandled);
    boolean existsByEventId(String eventId);
    int insertIgnore(String eventId, ZonedDateTime occurredAt);

    Set<String> findExistingEventIds(Collection<String> eventIds);
    int bulkInsertIgnore(List<EventHandledRecord> records);

    record EventHandledRecord(String eventId, ZonedDateTime occurredAt) {}
}
