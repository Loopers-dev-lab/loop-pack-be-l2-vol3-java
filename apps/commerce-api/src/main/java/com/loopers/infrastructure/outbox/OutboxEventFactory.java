package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.support.outbox.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventFactory {

    private final ObjectMapper objectMapper;

    public OutboxEvent create(String eventType, String aggregateType, String aggregateId,
                              String topic, Object eventPayload) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(eventPayload);
        } catch (Exception e) {
            throw new IllegalArgumentException("이벤트 직렬화 실패: " + eventType, e);
        }

        return OutboxEvent.create(
                UUID.randomUUID().toString(),
                eventType,
                aggregateType,
                aggregateId,
                payload,
                topic
        );
    }
}
