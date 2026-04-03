package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Outbox 이벤트 기록기.
 *
 * 도메인 이벤트 데이터를 직렬화하여 outbox_events 테이블에 저장한다.
 * 호출자의 트랜잭션에 참여하므로 별도 @Transactional 불필요.
 */
@RequiredArgsConstructor
@Component
public class OutboxRecorder {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final ObjectMapper objectMapper;

    public OutboxEvent record(String aggregateType, Long aggregateId, String eventType,
                              Map<String, Object> payload, String topic, String messageKey) {
        String payloadJson = serialize(payload);
        OutboxEvent event = new OutboxEvent(aggregateType, aggregateId, eventType, payloadJson, topic, messageKey);
        return outboxEventJpaRepository.save(event);
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Outbox 이벤트 payload 직렬화 실패", e);
        }
    }
}
