package com.loopers.infrastructure.eventhandled;

import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Override
    public boolean existsById(String eventId) {
        return eventHandledJpaRepository.existsById(eventId);
    }

    @Override
    public void save(EventHandled eventHandled) {
        eventHandledJpaRepository.save(eventHandled);
    }
}
