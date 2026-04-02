package com.loopers.domain.eventhandled;

/**
 * EventHandled Repository 인터페이스.
 */
public interface EventHandledRepository {

    EventHandled save(EventHandled eventHandled);

    boolean existsByEventId(String eventId);
}
