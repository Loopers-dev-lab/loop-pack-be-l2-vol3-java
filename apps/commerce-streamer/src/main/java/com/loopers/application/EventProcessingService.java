package com.loopers.application;

import com.loopers.domain.EventHandled;
import com.loopers.infrastructure.EventHandledRepository;
import com.loopers.event.Event;
import com.loopers.event.EventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class EventProcessingService {
    private final List<EventHandler> eventHandlers;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    @SuppressWarnings("unchecked")
    public void process(Event<EventPayload> event) {
        try {
            eventHandledRepository.save(new EventHandled(event.getEventId(), LocalDateTime.now()));
        } catch (DataIntegrityViolationException e) {
            log.info("[EventProcessingService] 이미 처리된 이벤트, eventId={}", event.getEventId());
            return;
        }

        for (EventHandler handler : eventHandlers) {
            if (handler.supports(event)) {
                handler.handle(event);
            }
        }
    }
}
