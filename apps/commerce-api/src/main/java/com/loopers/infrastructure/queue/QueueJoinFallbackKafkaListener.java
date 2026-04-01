package com.loopers.infrastructure.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.DomainEvents;
import com.loopers.domain.queue.WaitingQueueService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis 복구 후 대기열에 반영한다. 실패 시 재시도 토픽·DLQ로 넘긴다.
 */
@Component
@ConditionalOnProperty(name = "queue.fallback.enabled", havingValue = "true", matchIfMissing = true)
public class QueueJoinFallbackKafkaListener {

    private static final String EVENT_TYPE = DomainEvents.Type.QUEUE_JOIN_FALLBACK_REQUESTED;

    private final WaitingQueueService waitingQueueService;
    private final ObjectMapper objectMapper;

    public QueueJoinFallbackKafkaListener(WaitingQueueService waitingQueueService, ObjectMapper objectMapper) {
        this.waitingQueueService = waitingQueueService;
        this.objectMapper = objectMapper;
    }

    /**
     * Redis 장애 시 대기열 진입 의도를 Kafka로 발행한다.
     */
    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 5000, multiplier = 2.0, maxDelay = 30000L),
            kafkaTemplate = "kafkaTemplate",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    /**
     * Kafka 대기열 진입 의도를 수신한다.
     */
    @KafkaListener(
            topics = "${queue.fallback.topic-name:queue-join-fallback}",
            groupId = "${queue.fallback.consumer-group:loopers-queue-join-fallback-consumer}"
    )
    public void onMessage(ConsumerRecord<Object, Object> record, Acknowledgment acknowledgment) {
        Envelope envelope = parse(record.value());
        if (!EVENT_TYPE.equals(envelope.eventType())) {
            acknowledgment.acknowledge();
            return;
        }
        JsonNode data = envelope.data();
        String eventId = data.path("eventId").asText();
        long userId = data.path("userId").asLong();
        long score = data.path("score").asLong();
        waitingQueueService.joinQueueFromRecovery(eventId, userId, score);
        acknowledgment.acknowledge();
    }

    /**
     * Kafka 대기열 진입 의도를 수신 실패 시 DLQ로 넘긴다.
     */
    @DltHandler
    public void onDlt(ConsumerRecord<Object, Object> record, Acknowledgment acknowledgment) {
        acknowledgment.acknowledge();
    }

    /**
     * Kafka 대기열 진입 의도를 파싱한다.
     */
    private Envelope parse(Object rawValue) {
        try {
            byte[] bytes = rawValue instanceof byte[]
                    ? (byte[]) rawValue
                    : String.valueOf(rawValue).getBytes(StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(bytes);
            if (node.isTextual()) {
                node = objectMapper.readTree(node.asText());
            }
            return objectMapper.treeToValue(node, Envelope.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid queue join fallback envelope", e);
        }
    }

    private record Envelope(
            String eventId,
            String eventType,
            JsonNode data
    ) {
    }
}
