package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.outbox.EventHandled;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.infrastructure.outbox.EventHandledJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogEventConsumer {

    private final EventHandledJpaRepository eventHandledRepository;
    private final ProductMetricsJpaRepository productMetricsRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = KafkaTopics.CATALOG_EVENTS,
        groupId = "commerce-streamer-catalog",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    @Transactional
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                processRecord(record);
            } catch (Exception e) {
                log.error("[CatalogConsumer] 처리 실패: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), e);
            }
        }
        acknowledgment.acknowledge();
    }

    private void processRecord(ConsumerRecord<Object, Object> record) throws Exception {
        String rawPayload = new String((byte[]) record.value());
        JsonNode node = objectMapper.readTree(rawPayload);

        String eventId = node.get("eventId").asText();
        String eventType = node.get("eventType").asText();

        // 멱등 처리: 이미 처리된 이벤트면 skip
        if (eventHandledRepository.existsById(eventId)) {
            log.debug("[CatalogConsumer] 중복 이벤트 skip: eventId={}", eventId);
            return;
        }

        // occurredAt 기반 최신 이벤트만 반영 — ProductMetrics 내부에서 isAfter 비교
        ZonedDateTime occurredAt = objectMapper.treeToValue(node.get("occurredAt"), ZonedDateTime.class);
        JsonNode data = node.get("data");
        Long productId = data.get("productId").asLong();

        ProductMetrics metrics = productMetricsRepository.findById(productId)
            .orElseGet(() -> productMetricsRepository.save(new ProductMetrics(productId)));

        switch (eventType) {
            case "LIKE_ADDED" -> {
                metrics.increaseLikeCount(occurredAt);
                log.info("[CatalogConsumer] 좋아요 증가 처리: productId={}", productId);
            }
            case "LIKE_REMOVED" -> {
                metrics.decreaseLikeCount(occurredAt);
                log.info("[CatalogConsumer] 좋아요 감소 처리: productId={}", productId);
            }
            default -> log.warn("[CatalogConsumer] 알 수 없는 이벤트 타입: {}", eventType);
        }

        eventHandledRepository.save(new EventHandled(eventId));
    }
}
