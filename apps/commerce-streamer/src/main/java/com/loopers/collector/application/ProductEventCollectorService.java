package com.loopers.collector.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.infrastructure.collector.EventHandledJpaRepository;
import com.loopers.infrastructure.collector.EventHandledModel;
import com.loopers.infrastructure.collector.ProductMetricsJpaRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class ProductEventCollectorService {

    private static final String PRODUCT_LIKE_CHANGED = "PRODUCT_LIKE_CHANGED";

    private final EventHandledJpaRepository eventHandledJpaRepository;
    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final ObjectMapper objectMapper;

    public ProductEventCollectorService(
            EventHandledJpaRepository eventHandledJpaRepository,
            ProductMetricsJpaRepository productMetricsJpaRepository,
            ObjectMapper objectMapper) {
        this.eventHandledJpaRepository = eventHandledJpaRepository;
        this.productMetricsJpaRepository = productMetricsJpaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void process(ConsumerRecord<Object, Object> record) {
        ProductEventEnvelope envelope = parseEnvelope(record.value());
        if (envelope.eventId() == null || envelope.eventId().isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }

        try {
            eventHandledJpaRepository.saveAndFlush(EventHandledModel.of(
                    envelope.eventId(),
                    record.topic(),
                    record.partition(),
                    record.offset()
            ));
        } catch (DataIntegrityViolationException duplicate) {
            return;
        }

        if (!PRODUCT_LIKE_CHANGED.equals(envelope.eventType())) {
            return;
        }

        Long productId = envelope.data().path("productId").asLong();
        String action = envelope.data().path("action").asText();
        long delta = "LIKED".equals(action) ? 1L : -1L;
        productMetricsJpaRepository.applyLikeDeltaIfNewer(productId, delta, envelope.occurredAt());
    }

    private ProductEventEnvelope parseEnvelope(Object rawValue) {
        try {
            byte[] bytes = rawValue instanceof byte[]
                    ? (byte[]) rawValue
                    : String.valueOf(rawValue).getBytes(StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(bytes);
            if (node.isTextual()) {
                node = objectMapper.readTree(node.asText());
            }
            return objectMapper.treeToValue(node, ProductEventEnvelope.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid product domain event envelope", e);
        }
    }
}
