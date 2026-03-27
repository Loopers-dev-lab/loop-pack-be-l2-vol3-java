package com.loopers.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEventModel;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 이벤트 프로세서.
 *
 * <p>개별 Outbox 이벤트를 Kafka로 발행하고 상태를 갱신한다.
 * 발행 성공 시 PUBLISHED, 실패 시 FAILED/DEAD로 전이한다.</p>
 *
 * <p>Kafka 메시지 envelope 형식:
 * <pre>
 * {
 *   "eventId": 123,
 *   "eventType": "ORDER_CREATED",
 *   "aggregateType": "ORDER",
 *   "aggregateId": "456",
 *   "version": 1,
 *   "occurredAt": "2026-03-26T10:00:00",
 *   "payload": { ... }
 * }
 * </pre>
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventProcessor {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    @SuppressWarnings("unchecked")
    public boolean publishAndMark(OutboxEventModel event) {
        try {
            Map<String, Object> payload = objectMapper.readValue(event.getPayload(), Map.class);
            Map<String, Object> envelope = Map.of(
                "eventId", event.getEventId(),
                "eventType", event.getEventType(),
                "aggregateType", event.getAggregateType(),
                "aggregateId", event.getAggregateId(),
                "version", 1,
                "occurredAt", event.getCreatedAt().toString(),
                "payload", payload
            );

            String jsonMessage = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(
                event.getTopic(),
                event.getPartitionKey(),
                jsonMessage
            ).get(10, TimeUnit.SECONDS);

            event.markAsPublished();
            outboxRepository.save(event);
            return true;
        } catch (Exception e) {
            event.recordFailureWithBackoff(e.getMessage());
            outboxRepository.save(event);

            if (event.getRetryCount() >= 5) {
                log.error("[Outbox DEAD] eventId={}, retryCount={}",
                    event.getEventId(), event.getRetryCount(), e);
            } else {
                log.warn("[Outbox 재시도 예정] eventId={}, retry={}",
                    event.getEventId(), event.getRetryCount(), e);
            }
            return false;
        }
    }
}
