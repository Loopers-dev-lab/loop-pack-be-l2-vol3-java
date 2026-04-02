package com.loopers.infrastructure.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.DomainEvents;
import com.loopers.domain.queue.WaitingQueueService;
import com.loopers.infrastructure.metrics.QueueInfrastructureMetrics;
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
 * Kafka에 적재된 대기열 진입 복구 커맨드를 소비해 Redis에 반영한다. 실패 시 재시도 토픽·DLT로 넘긴다.
 * <p>
 * 정상 복구·DLT 진입 건수는 {@link com.loopers.infrastructure.metrics.QueueInfrastructureMetrics}에 위임한다.
 */
@Component
@ConditionalOnProperty(name = "queue.fallback.enabled", havingValue = "true", matchIfMissing = true)
public class QueueJoinFallbackKafkaListener {

    private static final String EVENT_TYPE = DomainEvents.Type.QUEUE_JOIN_FALLBACK_REQUESTED;

    private final WaitingQueueService waitingQueueService;
    private final ObjectMapper objectMapper;
    private final QueueInfrastructureMetrics queueInfrastructureMetrics;

    /**
     * @param queueInfrastructureMetrics 복구·DLT 처리 건수를 Micrometer에 기록하기 위한 공용 빈
     */
    public QueueJoinFallbackKafkaListener(
            WaitingQueueService waitingQueueService,
            ObjectMapper objectMapper,
            QueueInfrastructureMetrics queueInfrastructureMetrics
    ) {
        this.waitingQueueService = waitingQueueService;
        this.objectMapper = objectMapper;
        this.queueInfrastructureMetrics = queueInfrastructureMetrics;
    }

    /**
     * 재시도 토픽 설정
     * @param attempts 재시도 횟수
     * @param backoff 재시도 백오프
     * @param kafkaTemplate 카프카 템플릿
     * @param topicSuffixingStrategy 토픽 접미사 전략
     * @param dltStrategy DLT 토픽 메시지 처리 방법 지정
     */
    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 5000, multiplier = 2.0, maxDelay = 30000L),
            kafkaTemplate = "kafkaTemplate",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(
            topics = "${queue.fallback.topic-name:queue-join-fallback}",
            groupId = "${queue.fallback.consumer-group:loopers-queue-join-fallback-consumer}"
    )
    /**
     * Kafka에서 복구 커맨드를 수신해 Redis 대기열에 반영한다.
     */
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
        queueInfrastructureMetrics.recordKafkaJoinFallbackRecovered();
        acknowledgment.acknowledge();
    }
    
    /**
     * Kafka 대기열 진입 의도를 수신 실패 시 DLQ로 넘긴다.
     */
    @DltHandler
    public void onDlt(ConsumerRecord<Object, Object> record, Acknowledgment acknowledgment) {
        queueInfrastructureMetrics.recordKafkaJoinFallbackDlt();
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
