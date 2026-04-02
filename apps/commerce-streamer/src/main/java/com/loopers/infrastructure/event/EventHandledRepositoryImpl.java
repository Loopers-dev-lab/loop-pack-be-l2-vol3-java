package com.loopers.infrastructure.event;

import com.loopers.domain.event.EventHandled;
import com.loopers.domain.event.EventHandledRepository;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository jpaRepository;

    @Override
    public boolean existsByEventIdAndEventType(String eventId, String eventType) {
        return jpaRepository.existsByEventIdAndEventType(eventId, eventType);
    }

    @Override
    public boolean existsByEntityIdAndEventTypeAndOccurredAtGreaterThanEqual(String entityId, String eventType, ZonedDateTime occurredAt) {
        return jpaRepository.existsByEntityIdAndEventTypeAndOccurredAtGreaterThanEqual(entityId, eventType, occurredAt);
    }

    @Override
    public EventHandled save(EventHandled eventHandled) {
        return EventHandledEntity.toDomain(jpaRepository.save(new EventHandledEntity(eventHandled)));
    }
}
