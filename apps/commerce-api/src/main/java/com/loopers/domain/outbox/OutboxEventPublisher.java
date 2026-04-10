package com.loopers.domain.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void publish(String topic, String partitionKey, String eventType, Map<String, Object> data) {
        OutboxEventPayload payload = new OutboxEventPayload(
            UUID.randomUUID().toString(),
            eventType,
            ZonedDateTime.now(),
            data
        );
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxEventRepository.save(new OutboxEvent(payload.eventId(), topic, partitionKey, json));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Outbox 직렬화 실패: eventType=" + eventType, e);
        }
    }
}
