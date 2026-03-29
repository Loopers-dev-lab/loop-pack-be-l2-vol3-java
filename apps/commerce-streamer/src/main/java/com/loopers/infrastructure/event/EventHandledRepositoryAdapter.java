package com.loopers.infrastructure.event;

import com.loopers.domain.event.EventHandled;
import com.loopers.domain.event.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EventHandledRepositoryAdapter implements EventHandledRepository {

    private final EventHandledJpaRepository jpaRepository;

    @Override
    public void save(EventHandled eventHandled) {
        jpaRepository.save(eventHandled);
    }

    @Override
    public boolean existsByTopicAndEventId(String topic, String eventId) {
        return jpaRepository.existsByTopicAndEventId(topic, eventId);
    }
}
