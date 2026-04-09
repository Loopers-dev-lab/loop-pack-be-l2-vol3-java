package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.idempotent.IdempotentProcessor;
import com.loopers.application.metrics.MetricsService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventConsumer {

    private final IdempotentProcessor idempotentProcessor;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {KafkaTopics.PRODUCT_VIEW_EVENTS, KafkaTopics.PRODUCT_INTERACTION_EVENTS}, groupId = "metrics-aggregation",
            containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
        for (int i = 0; i < records.size(); i++) {
            try {
                processRecord(records.get(i));
            } catch (Exception e) {
                throw new BatchListenerFailedException("catalog 이벤트 처리 실패", e, i);
            }
        }
        ack.acknowledge();
    }

    private static final String TOPIC = "catalog-consumer";
    private static final String GROUP_ID = "metrics-aggregation";

    private void processRecord(ConsumerRecord<String, byte[]> record) throws Exception {
        JsonNode node = objectMapper.readTree(record.value());
        String eventId = node.path("eventId").asText();
        String eventType = node.path("eventType").asText();
        JsonNode payload = objectMapper.readTree(node.path("payload").asText());

        String idempotencyKey = GROUP_ID + ":" + eventId;

        switch (eventType) {
            case "product.liked" -> idempotentProcessor.process(idempotencyKey, eventType, TOPIC, GROUP_ID,
                    () -> metricsService.incrementLikeCount(payload.path("productId").asLong(), 1));
            case "product.unliked" -> idempotentProcessor.process(idempotencyKey, eventType, TOPIC, GROUP_ID,
                    () -> metricsService.incrementLikeCount(payload.path("productId").asLong(), -1));
            case "product.viewed" -> idempotentProcessor.process(idempotencyKey, eventType, TOPIC, GROUP_ID,
                    () -> metricsService.incrementViewCount(payload.path("productId").asLong(), 1));
            default -> log.warn("미지원 catalog 이벤트: eventType={}", eventType);
        }
    }
}
