package com.loopers.domain.eventhandled;

public interface EventHandledRepository {

    boolean existsById(String eventId);

    void save(EventHandled eventHandled);
}
