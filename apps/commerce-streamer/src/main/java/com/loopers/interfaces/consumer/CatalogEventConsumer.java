package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.MetricsEventService;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventConsumer {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final MetricsEventService metricsEventService;

    @KafkaListener(
        topics = "catalog.events.topic-v1",
        groupId = "streamer-metrics",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<String, byte[]> record : records) {
            try {
                JsonNode node = objectMapper.readTree(record.value());
                String eventId = node.get("eventId").asText();
                String eventType = node.get("eventType").asText();
                long productId = node.get("productId").asLong();
                int quantity = node.has("quantity") ? node.get("quantity").asInt() : 0;
                BigDecimal price = node.has("price") ? node.get("price").decimalValue() : null;
                Instant occurredAt = node.has("occurredAt")
                    ? Instant.parse(node.get("occurredAt").asText())
                    : Instant.now();

                metricsEventService.process(record.topic(), eventId, eventType, productId, quantity, price, occurredAt);
            } catch (Exception e) {
                log.warn("카탈로그 이벤트 처리 실패 - offset: {}, partition: {}",
                    record.offset(), record.partition(), e);
            }
        }
        acknowledgment.acknowledge();
    }
}
