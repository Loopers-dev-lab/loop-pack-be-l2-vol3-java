package com.loopers.infrastructure.event;

import com.loopers.domain.event.EventHandled;
import com.loopers.domain.event.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Override
    public boolean existsByEventId(Long eventId) {
        return eventHandledJpaRepository.existsByEventId(eventId);
    }

    @Override
    public EventHandled save(EventHandled eventHandled) {
        return eventHandledJpaRepository.save(eventHandled);
    }
}
