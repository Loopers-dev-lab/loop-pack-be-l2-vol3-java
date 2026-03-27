package com.loopers.infrastructure.event;

import com.loopers.domain.event.EventHandled;
import com.loopers.domain.event.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;

@Repository
@RequiredArgsConstructor
public class EventHandledRepositoryImpl implements EventHandledRepository {
    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Override
    public EventHandled save(EventHandled eventHandled) {
        return eventHandledJpaRepository.save(eventHandled);
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return eventHandledJpaRepository.existsByEventId(eventId);
    }

    @Override
    public int insertIgnore(String eventId, ZonedDateTime occurredAt) {
        return eventHandledJpaRepository.insertIgnore(eventId, occurredAt);
    }
}
