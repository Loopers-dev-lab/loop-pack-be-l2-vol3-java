package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ConsumerMetrics;
import com.loopers.application.metrics.InteractionMetricProcessor;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductInteractionConsumer {

    private static final String GROUP_ID = "ranking-consumer";
    private static final String TOPIC = KafkaTopics.PRODUCT_INTERACTION_EVENTS;

    private final InteractionMetricProcessor interactionMetricProcessor;
    private final ObjectMapper objectMapper;
    private final ConsumerMetrics consumerMetrics;

    @KafkaListener(
            id = "productInteractionConsumer",
            topics = KafkaTopics.PRODUCT_INTERACTION_EVENTS,
            groupId = GROUP_ID,
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
        for (int i = 0; i < records.size(); i++) {
            try {
                processRecord(records.get(i));
            } catch (Exception e) {
                throw new BatchListenerFailedException("interaction 이벤트 처리 실패", e, i);
            }
        }
        ack.acknowledge();
    }

    private void processRecord(ConsumerRecord<String, byte[]> record) throws Exception {
        JsonNode envelope = objectMapper.readTree(record.value());
        String eventId = envelope.path("eventId").asText();
        String eventType = envelope.path("eventType").asText();

        JsonNode payload = objectMapper.readTree(envelope.path("payload").asText());
        Long productId = payload.path("productId").asLong();
        Instant occurredAt = Instant.parse(payload.path("occurredAt").asText());

        if (!"product.liked".equals(eventType) && !"product.unliked".equals(eventType)) {
            log.warn("미지원 interaction 이벤트: eventType={}", eventType);
            return;
        }

        interactionMetricProcessor.process(eventId, eventType, TOPIC, GROUP_ID, productId, occurredAt);
    }
}
