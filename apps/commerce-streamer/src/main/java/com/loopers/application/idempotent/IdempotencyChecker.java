package com.loopers.application.idempotent;

import com.loopers.domain.idempotent.EventHandled;
import com.loopers.domain.idempotent.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class IdempotencyChecker {

    private final EventHandledRepository eventHandledRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryMark(String eventId, String eventType) {
        try {
            eventHandledRepository.save(EventHandled.create(eventId, eventType));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
