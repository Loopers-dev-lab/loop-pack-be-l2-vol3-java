package com.loopers.infrastructure.persistence.jpa.eventhandled;

import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * EventHandledRepository 구현체.
 */
@Repository
@RequiredArgsConstructor
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository jpaRepository;

    @Override
    public EventHandled save(EventHandled eventHandled) {
        EventHandledJpaEntity entity = EventHandledMapper.toJpaEntity(eventHandled);
        EventHandledJpaEntity saved = jpaRepository.save(entity);
        return EventHandledMapper.toDomain(saved);
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return jpaRepository.existsByEventId(eventId);
    }
}
