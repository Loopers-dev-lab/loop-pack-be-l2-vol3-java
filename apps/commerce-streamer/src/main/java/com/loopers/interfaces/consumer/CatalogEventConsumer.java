package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ProductMetricsApp;
import com.loopers.infrastructure.kafka.StreamerKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventConsumer {

    private static final String TOPIC = "catalog-events";

    private final ProductMetricsApp productMetricsApp;
    private final ObjectMapper objectMapper;

    private static final java.util.Set<String> SUPPORTED_EVENT_TYPES =
            java.util.Set.of("LikedEvent", "LikeRemovedEvent");

    @KafkaListener(
            topics = TOPIC,
            groupId = "commerce-streamer-catalog",
            containerFactory = StreamerKafkaConfig.DLQ_BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<Object, Object> record = records.get(i);
            try {
                CatalogEventPayload payload = parse(record);
                if (!SUPPORTED_EVENT_TYPES.contains(payload.eventType())) {
                    log.warn("[CATALOG_EVENT] 미지원 eventType={}, offset={} — 건너뜀",
                            payload.eventType(), record.offset());
                    continue;
                }
                productMetricsApp.applyLikeDelta(
                        payload.eventId(),
                        payload.productDbId(),
                        payload.delta(),
                        payload.likedAt()
                );
            } catch (Exception e) {
                log.error("[CATALOG_EVENT_FAILED] offset={}, key={}", record.offset(), record.key(), e);
                throw new org.springframework.kafka.listener.BatchListenerFailedException(
                        "catalog-events processing failed at index " + i, e, i);
            }
        }
        acknowledgment.acknowledge();
    }

    private CatalogEventPayload parse(ConsumerRecord<Object, Object> record) {
        try {
            return objectMapper.readValue((byte[]) record.value(), CatalogEventPayload.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize CatalogEventPayload", e);
        }
    }
}
