package com.loopers.domain.event;

public interface EventHandledRepository {
    boolean existsByEventId(String eventId);
    EventHandledModel save(EventHandledModel model);
}
