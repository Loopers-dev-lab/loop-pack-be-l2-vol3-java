package com.loopers.infrastructure.event;

import com.loopers.domain.event.EventHandledModel;
import com.loopers.domain.event.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository jpaRepository;

    @Override
    public boolean existsByEventId(String eventId) {
        return jpaRepository.existsByEventId(eventId);
    }

    @Override
    public EventHandledModel save(EventHandledModel model) {
        return jpaRepository.save(model);
    }
}
