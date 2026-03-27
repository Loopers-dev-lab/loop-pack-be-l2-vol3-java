package com.loopers.collector.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.collector.idempotency.LightweightEventIdempotency;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class ProductEventCollectorService {

    private static final String USER_REGISTERED = "USER_REGISTERED";
    private static final String BRAND_REGISTERED = "BRAND_REGISTERED";
    private static final String CART_ITEM_ADDED = "CART_ITEM_ADDED";

    private final ProductEventCollectorDatabaseService databaseService;
    private final LightweightEventIdempotency lightweightEventIdempotency;
    private final ObjectMapper objectMapper;
    private final Counter processedCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;

    public ProductEventCollectorService(
            ProductEventCollectorDatabaseService databaseService,
            LightweightEventIdempotency lightweightEventIdempotency,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.databaseService = databaseService;
        this.lightweightEventIdempotency = lightweightEventIdempotency;
        this.objectMapper = objectMapper;
        this.processedCounter = meterRegistry.counter("kafka.collector.events.processed");
        this.duplicateCounter = meterRegistry.counter("kafka.collector.events.duplicate");
        this.failedCounter = meterRegistry.counter("kafka.collector.events.failed");
    }

    /**
     * 메트릭이 없는 USER/BRAND/CART 이벤트는 Redis 멱등만 수행하고 event_handled DB 쓰기를 생략한다.
     * Redis 장애 시 DB로 대체하지 않으며, 예외는 Kafka 재시도·DLQ로 처리한다.
     * 그 외는 {@link ProductEventCollectorDatabaseService}에서 트랜잭션으로 event_handled + product_metrics를 처리한다.
     */
    public void process(ConsumerRecord<Object, Object> record) {
        ProductEventEnvelope envelope;
        try {
            envelope = parseEnvelope(record.value());
        } catch (IllegalArgumentException e) {
            failedCounter.increment();
            throw e;
        }
        if (envelope.eventId() == null || envelope.eventId().isBlank()) {
            failedCounter.increment();
            throw new IllegalArgumentException("eventId is required");
        }

        if (isLightweightLogOnlyEvent(envelope.eventType())) {
            if (!lightweightEventIdempotency.tryClaimFirstDelivery(envelope.eventId())) {
                duplicateCounter.increment();
                return;
            }
            processedCounter.increment();
            return;
        }

        databaseService.processDb(record, envelope);
    }

    private static boolean isLightweightLogOnlyEvent(String eventType) {
        return USER_REGISTERED.equals(eventType)
                || BRAND_REGISTERED.equals(eventType)
                || CART_ITEM_ADDED.equals(eventType);
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
            return objectMapper.readerFor(ProductEventEnvelope.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(node);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid product domain event envelope", e);
        }
    }
}
