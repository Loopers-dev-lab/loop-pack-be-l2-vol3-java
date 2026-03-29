package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void save(String aggregateType, Long aggregateId, String eventType, Object payload) {
        try {
            Map<String, Object> enriched = new LinkedHashMap<>();
            enriched.put("eventId", UUID.randomUUID().toString());
            if (payload instanceof Map<?, ?> map) {
                map.forEach((k, v) -> enriched.put(String.valueOf(k), v));
            }
            String json = objectMapper.writeValueAsString(enriched);
            OutboxEvent event = OutboxEvent.create(aggregateType, aggregateId, eventType, json);
            outboxEventRepository.save(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                String.format("Outbox 이벤트 직렬화 실패 - aggregateType: %s, aggregateId: %d, eventType: %s",
                    aggregateType, aggregateId, eventType), e);
        }
    }
}
