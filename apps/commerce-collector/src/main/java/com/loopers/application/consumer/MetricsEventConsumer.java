package com.loopers.application.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.ranking.RankingBatchProcessor;
import com.loopers.application.ranking.RankingBatchProcessor.ScoreEntry;
import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.event.EventEnvelope;
import com.loopers.event.EventType;
import com.loopers.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 메트릭 수집을 위한 Kafka Consumer.
 * catalog-events, order-events 토픽에서 이벤트를 수신하여 메트릭을 집계.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsEventConsumer {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;
    private final ObjectMapper objectMapper;
    private final RankingBatchProcessor rankingBatchProcessor;

    @KafkaListener(
        topics = {KafkaTopics.CATALOG_EVENTS, KafkaTopics.ORDER_EVENTS},
        groupId = "metrics-collector",
        batch = "true"
    )
    @Transactional
    public void consumeBatch(List<EventEnvelope> envelopes, Acknowledgment ack) {
        try {
            processEventsBatch(envelopes);
            ack.acknowledge();
            log.debug("Batch processed: count={}", envelopes.size());
        } catch (Exception e) {
            log.error("Failed to process batch: count={}", envelopes.size(), e);
            // 처리 실패 시 acknowledge하지 않아 재처리됨
        }
    }

    /**
     * 단건 이벤트 처리 (테스트 가능하도록 분리)
     * 내부적으로 배치 처리를 호출합니다.
     */
    @Transactional
    public void processEvent(EventEnvelope envelope) {
        processEventsBatch(List.of(envelope));
    }

    /**
     * 배치 이벤트 처리.
     * product_metrics는 개별 처리하고, 랭킹 점수는 배치로 일괄 처리합니다.
     *
     * @param envelopes 이벤트 목록
     */
    @Transactional
    public void processEventsBatch(List<EventEnvelope> envelopes) {
        if (envelopes == null || envelopes.isEmpty()) {
            return;
        }

        List<ScoreEntry> scoreEntries = new ArrayList<>();

        for (EventEnvelope envelope : envelopes) {
            // 1. 멱등성 체크
            if (eventHandledRepository.existsByEventId(envelope.eventId())) {
                log.debug("Event already handled: eventId={}", envelope.eventId());
                continue;
            }

            // 2. 이벤트 타입별 처리 (product_metrics 개별 처리, 랭킹 점수 수집)
            EventType eventType = EventType.valueOf(envelope.eventType());
            ScoreEntry scoreEntry = switch (eventType) {
                case LIKE_CREATED -> handleLikeCreated(envelope);
                case LIKE_CANCELED -> handleLikeCanceled(envelope);
                case PRODUCT_VIEWED -> handleProductViewed(envelope);
                case ORDER_COMPLETED -> handleOrderCompleted(envelope);
                default -> null;
            };

            if (scoreEntry != null) {
                scoreEntries.add(scoreEntry);
            }

            // 3. 처리 완료 기록
            eventHandledRepository.save(EventHandled.create(envelope.eventId(), envelope.eventType()));
            log.debug("Event processed: eventId={}, type={}", envelope.eventId(), envelope.eventType());
        }

        // 4. 랭킹 점수 배치 처리
        if (!scoreEntries.isEmpty()) {
            rankingBatchProcessor.addScoresBatch(scoreEntries);
            log.debug("Batch ranking scores processed: count={}", scoreEntries.size());
        }
    }

    private ScoreEntry handleLikeCreated(EventEnvelope envelope) {
        Long productId = Long.parseLong(envelope.aggregateId());
        ProductMetrics metrics = productMetricsRepository.getOrCreate(productId);

        validateEventOrder(metrics, envelope);

        metrics.incrementLikeCount();
        metrics.updateLastEventTimestamp(envelope.timestamp());
        productMetricsRepository.save(metrics);

        return ScoreEntry.of(productId, EventType.LIKE_CREATED, 1.0);
    }

    private ScoreEntry handleLikeCanceled(EventEnvelope envelope) {
        Long productId = Long.parseLong(envelope.aggregateId());
        ProductMetrics metrics = productMetricsRepository.getOrCreate(productId);

        validateEventOrder(metrics, envelope);

        metrics.decrementLikeCount();
        metrics.updateLastEventTimestamp(envelope.timestamp());
        productMetricsRepository.save(metrics);

        return ScoreEntry.of(productId, EventType.LIKE_CANCELED, 1.0);
    }

    private ScoreEntry handleProductViewed(EventEnvelope envelope) {
        Long productId = Long.parseLong(envelope.aggregateId());
        ProductMetrics metrics = productMetricsRepository.getOrCreate(productId);

        validateEventOrder(metrics, envelope);

        metrics.incrementViewCount();
        metrics.updateLastEventTimestamp(envelope.timestamp());
        productMetricsRepository.save(metrics);

        return ScoreEntry.of(productId, EventType.PRODUCT_VIEWED, 1.0);
    }

    private ScoreEntry handleOrderCompleted(EventEnvelope envelope) {
        try {
            JsonNode payload = objectMapper.readTree(envelope.payload());
            Long productId = payload.get("productId").asLong();
            int quantity = payload.get("quantity").asInt();
            long totalAmount = payload.get("totalAmount").asLong();

            ProductMetrics metrics = productMetricsRepository.getOrCreate(productId);

            validateEventOrder(metrics, envelope);

            metrics.addOrder(quantity, totalAmount);
            metrics.updateLastEventTimestamp(envelope.timestamp());
            productMetricsRepository.save(metrics);

            return ScoreEntry.of(productId, EventType.ORDER_COMPLETED, (double) totalAmount);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse order completed event payload: eventId={}", envelope.eventId(), e);
            throw new RuntimeException("Failed to parse event payload", e);
        }
    }

    /**
     * 이벤트 순서 검증.
     * Delta 연산에서는 모든 이벤트를 처리해야 하므로 경고만 로그.
     * State 기반 연산에서는 이 메서드를 활용해 오래된 이벤트를 스킵할 수 있음.
     */
    private void validateEventOrder(ProductMetrics metrics, EventEnvelope envelope) {
        if (!metrics.isNewerEvent(envelope.timestamp())) {
            log.warn("Out-of-order event detected: eventId={}, eventTimestamp={}, lastEventTimestamp={}, productId={}",
                envelope.eventId(),
                envelope.timestamp(),
                metrics.getLastEventTimestamp(),
                envelope.aggregateId());
        }
    }
}
