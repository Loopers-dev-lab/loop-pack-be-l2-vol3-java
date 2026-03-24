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

    @KafkaListener(
            topics = TOPIC,
            groupId = "commerce-streamer-catalog",
            containerFactory = StreamerKafkaConfig.DLQ_BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            CatalogEventPayload payload = parse(record);
            productMetricsApp.applyLikeDelta(
                    payload.eventId(),
                    payload.productDbId(),
                    payload.delta(),
                    payload.likedAt()
            );
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
