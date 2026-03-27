package com.loopers.infrastructure.idempotency;

import com.loopers.domain.idempotency.EventHandled;
import com.loopers.domain.idempotency.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Override
    public boolean existsByEventId(String eventId) {
        return eventHandledJpaRepository.existsByEventId(eventId);
    }

    @Override
    public EventHandled save(EventHandled eventHandled) {
        return eventHandledJpaRepository.save(eventHandled);
    }
}