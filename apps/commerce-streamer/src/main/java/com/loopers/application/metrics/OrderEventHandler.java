package com.loopers.application.metrics;

import com.loopers.domain.event.EventHandled;
import com.loopers.domain.event.EventHandledRepository;
import com.loopers.interfaces.consumer.OutboxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * order-events 메시지 처리 핸들러.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OrderEventHandler {

    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void handle(OutboxMessage message) {
        // 멱등성 체크
        if (eventHandledRepository.existsByEventId(message.eventId())) {
            log.debug("[OrderEventHandler] 이미 처리된 이벤트 skip: eventId={}", message.eventId());
            return;
        }

        if ("ORDER_CREATED".equals(message.eventType())) {
            log.info("[OrderEventHandler] 주문 생성 이벤트 처리: eventId={}, aggregateId={}",
                    message.eventId(), message.aggregateId());
        } else {
            log.warn("[OrderEventHandler] 알 수 없는 이벤트 타입: {}", message.eventType());
        }

        // 처리 기록 저장 (멱등성 보장)
        eventHandledRepository.save(new EventHandled(message.eventId(), message.eventType()));
    }
}
