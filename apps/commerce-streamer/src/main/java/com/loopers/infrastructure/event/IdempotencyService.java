package com.loopers.infrastructure.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멱등성 서비스 — Consumer에서 중복 메시지 방지
 *
 * 비즈니스 로직과 같은 TX에서 호출해야 한다.
 * → 비즈니스 실패 시 멱등 기록도 롤백 → 재처리 가능
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final EventHandledJpaRepository eventHandledJpaRepository;

    public IdempotencyService(EventHandledJpaRepository eventHandledJpaRepository) {
        this.eventHandledJpaRepository = eventHandledJpaRepository;
    }

    public boolean isAlreadyHandled(String eventId) {
        return eventHandledJpaRepository.existsByEventId(eventId);
    }

    @Transactional
    public boolean markHandled(String eventId, String topic) {
        try {
            eventHandledJpaRepository.save(EventHandledEntity.of(eventId, topic));
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("[Idempotency] 중복 감지 (UNIQUE 위반) — eventId={}", eventId);
            return false;
        }
    }
}
