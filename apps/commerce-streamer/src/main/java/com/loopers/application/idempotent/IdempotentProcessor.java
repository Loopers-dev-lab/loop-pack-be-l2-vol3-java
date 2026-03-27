package com.loopers.application.idempotent;

import com.loopers.domain.idempotent.EventHandled;
import com.loopers.domain.idempotent.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentProcessor {

    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public boolean process(String eventId, String eventType, Runnable handler) {
        if (eventHandledRepository.existsByEventId(eventId)) {
            log.debug("이미 처리된 이벤트 스킵: eventId={}", eventId);
            return false;
        }

        handler.run();
        eventHandledRepository.save(EventHandled.create(eventId, eventType));
        return true;
    }
}
