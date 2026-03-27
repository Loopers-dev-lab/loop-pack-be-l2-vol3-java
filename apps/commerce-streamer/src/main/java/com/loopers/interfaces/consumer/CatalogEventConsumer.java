package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.idempotent.IdempotentProcessor;
import com.loopers.application.metrics.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventConsumer {

    private final IdempotentProcessor idempotentProcessor;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "catalog-events", groupId = "commerce-streamer")
    public void consume(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            String eventId = node.path("eventId").asText();
            String eventType = node.path("eventType").asText();
            JsonNode payload = objectMapper.readTree(node.path("payload").asText());

            switch (eventType) {
                case "product.liked" -> idempotentProcessor.process(eventId, eventType,
                        () -> metricsService.incrementLikeCount(payload.path("productId").asLong(), 1));
                case "product.unliked" -> idempotentProcessor.process(eventId, eventType,
                        () -> metricsService.incrementLikeCount(payload.path("productId").asLong(), -1));
                case "product.viewed" -> idempotentProcessor.process(eventId, eventType,
                        () -> metricsService.incrementViewCount(payload.path("productId").asLong(), 1));
                default -> log.warn("미지원 catalog 이벤트: eventType={}", eventType);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("catalog 이벤트 처리 실패: offset={}", record.offset(), e);
            throw new RuntimeException(e);
        }
    }
}
