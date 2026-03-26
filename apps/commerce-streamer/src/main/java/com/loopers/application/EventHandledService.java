package com.loopers.application;

import com.loopers.domain.event.EventHandledModel;
import com.loopers.domain.event.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class EventHandledService {

    private final EventHandledRepository eventHandledRepository;

    /**
     * 이미 처리된 이벤트인지 확인한다.
     * @return true면 이미 처리됨 (skip해야 함)
     */
    public boolean isAlreadyHandled(String eventId) {
        return eventHandledRepository.existsByEventId(eventId);
    }

    @Transactional
    public void markHandled(String eventId) {
        eventHandledRepository.save(new EventHandledModel(eventId));
    }
}
