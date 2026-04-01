package com.loopers.infrastructure.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.DomainEvents;
import com.loopers.domain.queue.QueueJoinFallbackPublisher;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis 장애 시 대기열 진입 의도를 Kafka로 발행한다. Outbox 릴레이와 동일한 envelope 형태를 맞춘다.
 */
@Component
@ConditionalOnProperty(name = "queue.fallback.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaQueueJoinFallbackPublisher implements QueueJoinFallbackPublisher {

    private static final String HEADER_EVENT_ID = "eventId";
    private static final String HEADER_EVENT_TYPE = "eventType";

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final long sendAckTimeoutMs;

    public KafkaQueueJoinFallbackPublisher(
            KafkaTemplate<Object, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${queue.fallback.topic-name:queue-join-fallback}") String topic,
            @Value("${queue.fallback.send-ack-timeout-ms:5000}") long sendAckTimeoutMs
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.sendAckTimeoutMs = sendAckTimeoutMs;
    }

    /**
     * Redis 장애 시 대기열 진입 의도를 Kafka로 발행한다.
     * Outbox 릴레이와 동일한 envelope 형태를 맞춘다.
     */
    @Override
    public void publish(String eventId, Long userId, long score, String requestId) {
        try {
            String json = buildEnvelopeJson(eventId, userId, score, requestId);
            ProducerRecord<Object, Object> record = new ProducerRecord<>(
                    topic,
                    null,
                    String.valueOf(userId),
                    json
            );
            record.headers().add(HEADER_EVENT_ID, requestId.getBytes(StandardCharsets.UTF_8));
            record.headers().add(HEADER_EVENT_TYPE, DomainEvents.Type.QUEUE_JOIN_FALLBACK_REQUESTED.getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(record).get(sendAckTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "대기열 비동기 접수(Kafka)에 실패했습니다.", e);
        }
    }

    /**
     * Outbox 릴레이와 동일한 envelope 형태를 맞춘 JSON 문자열을 반환
     */
    private String buildEnvelopeJson(String eventId, Long userId, long score, String requestId) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventId", eventId);
        data.put("userId", userId);
        data.put("score", score);
        data.put("requestId", requestId);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", requestId);
        envelope.put("eventType", DomainEvents.Type.QUEUE_JOIN_FALLBACK_REQUESTED);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("partitionKey", String.valueOf(userId));
        envelope.put("data", data);
        return objectMapper.writeValueAsString(envelope);
    }
}
