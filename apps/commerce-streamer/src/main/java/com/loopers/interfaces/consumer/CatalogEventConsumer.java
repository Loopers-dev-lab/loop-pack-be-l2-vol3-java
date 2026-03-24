package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.MetricsApplicationService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.eventhandled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogEventConsumer {

    private static final String DLQ_TOPIC = "catalog-events.dlq";

    private final MetricsApplicationService metricsApplicationService;
    private final EventHandledRepository eventHandledRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @KafkaListener(
        topics = "catalog-events",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<String, byte[]> record : records) {
            try {
                JsonNode envelope = parseEnvelope(record.value());
                String eventId = envelope.get("eventId").asText();
                String eventType = envelope.get("eventType").asText();

                if (eventHandledRepository.existsById(eventId)) {
                    log.debug("[CatalogEvent] 이미 처리된 이벤트 skip: eventId={}", eventId);
                    continue;
                }

                JsonNode data = envelope.get("data");
                processEvent(eventId, eventType, data);
                log.info("[CatalogEvent] 처리 완료: eventId={}, eventType={}", eventId, eventType);
            } catch (Exception e) {
                log.error("[CatalogEvent] 처리 실패 → DLQ 전송: offset={}, error={}",
                    record.offset(), e.getMessage(), e);
                sendToDlq(record);
            }
        }
        acknowledgment.acknowledge();
    }

    private void processEvent(String eventId, String eventType, JsonNode data) {
        Long productId = data.get("productId").asLong();

        switch (eventType) {
            case "LIKED" -> metricsApplicationService.incrementLikeCount(eventId, productId);
            case "UNLIKED" -> metricsApplicationService.decrementLikeCount(eventId, productId);
            case "PRODUCT_VIEWED" -> metricsApplicationService.incrementViewCount(eventId, productId);
            default -> log.warn("[CatalogEvent] 알 수 없는 이벤트 타입: {}", eventType);
        }
    }

    private void sendToDlq(ConsumerRecord<String, byte[]> record) {
        try {
            kafkaTemplate.send(DLQ_TOPIC, record.key(), record.value());
        } catch (Exception e) {
            log.error("[CatalogEvent] DLQ 전송 실패: offset={}, error={}", record.offset(), e.getMessage());
        }
    }

    private JsonNode parseEnvelope(byte[] value) throws IOException {
        JsonNode node = objectMapper.readTree(value);
        if (node.isTextual()) {
            node = objectMapper.readTree(node.asText());
        }
        return node;
    }
}
