package com.loopers.domain.event;

public interface EventHandledRepository {

    void save(EventHandled eventHandled);

    boolean existsByTopicAndEventId(String topic, String eventId);
}
