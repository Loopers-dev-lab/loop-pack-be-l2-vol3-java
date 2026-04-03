package com.loopers.domain.event;

public interface EventHandledRepository {

    boolean existsByEventId(Long eventId);

    EventHandled save(EventHandled eventHandled);
}
