package com.loopers.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.DomainEventPublisher;
import com.loopers.domain.event.EventOutbox;
import com.loopers.domain.event.EventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DomainEventPublisherImpl implements DomainEventPublisher {

    private final EventOutboxRepository eventOutboxRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(String aggregateType, String aggregateId, String eventType, Object payload, Object event) {
        String json = serializePayload(payload);
        eventOutboxRepository.save(EventOutbox.create(aggregateType, aggregateId, eventType, json));
        applicationEventPublisher.publishEvent(event);
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("이벤트 페이로드 직렬화 실패", e);
        }
    }
}
