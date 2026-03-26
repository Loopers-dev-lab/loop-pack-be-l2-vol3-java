package com.loopers.domain.eventhandled;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventHandledService {

    private final EventHandledRepository eventHandledRepository;

    @Transactional(readOnly = true)
    public boolean isAlreadyHandled(String eventId) {
        return eventHandledRepository.existsByEventId(eventId);
    }

    @Transactional
    public void markAsHandled(String eventId) {
        eventHandledRepository.save(EventHandled.create(eventId));
    }
}
