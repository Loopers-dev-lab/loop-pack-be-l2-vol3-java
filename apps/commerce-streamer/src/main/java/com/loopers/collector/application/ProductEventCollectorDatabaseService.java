package com.loopers.collector.application;

import com.loopers.infrastructure.collector.EventHandledJpaRepository;
import com.loopers.infrastructure.collector.EventHandledModel;
import com.loopers.infrastructure.collector.ProductMetricsJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * product_metrics 갱신이 필요한 이벤트만: event_handled INSERT + 메트릭 갱신을 한 트랜잭션으로 묶는다.
 */
@Service
public class ProductEventCollectorDatabaseService {

    private static final String PRODUCT_LIKE_CHANGED = "PRODUCT_LIKE_CHANGED";
    private static final String PRODUCT_VIEWED = "PRODUCT_VIEWED";
    private static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";

    private final EventHandledJpaRepository eventHandledJpaRepository;
    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final Counter processedCounter;
    private final Counter duplicateCounter;

    public ProductEventCollectorDatabaseService(
            EventHandledJpaRepository eventHandledJpaRepository,
            ProductMetricsJpaRepository productMetricsJpaRepository,
            MeterRegistry meterRegistry) {
        this.eventHandledJpaRepository = eventHandledJpaRepository;
        this.productMetricsJpaRepository = productMetricsJpaRepository;
        this.processedCounter = meterRegistry.counter("kafka.collector.events.processed");
        this.duplicateCounter = meterRegistry.counter("kafka.collector.events.duplicate");
    }

    @Transactional
    public void processDb(ConsumerRecord<Object, Object> record, ProductEventEnvelope envelope) {
        try {
            eventHandledJpaRepository.saveAndFlush(EventHandledModel.of(
                    envelope.eventId(),
                    record.topic(),
                    record.partition(),
                    record.offset()
            ));
        } catch (DataIntegrityViolationException duplicate) {
            duplicateCounter.increment();
            return;
        }

        String eventType = envelope.eventType();
        if (PRODUCT_LIKE_CHANGED.equals(eventType)) {
            Long productId = envelope.data().path("productId").asLong();
            String action = envelope.data().path("action").asText();
            long delta = "LIKED".equals(action) ? 1L : -1L;
            productMetricsJpaRepository.applyLikeDeltaIfNewer(productId, delta, envelope.occurredAt());
            processedCounter.increment();
            return;
        }
        if (PRODUCT_VIEWED.equals(eventType)) {
            long productId = envelope.data().path("productId").asLong();
            productMetricsJpaRepository.applyViewDeltaIfNewer(productId, 1L, envelope.occurredAt());
            processedCounter.increment();
            return;
        }
        if (PAYMENT_COMPLETED.equals(eventType)) {
            var lines = envelope.data().path("lines");
            if (lines.isArray()) {
                for (var line : lines) {
                    long productId = line.path("productId").asLong();
                    long qty = line.path("quantity").asLong();
                    productMetricsJpaRepository.applySoldDeltaIfNewer(productId, qty, envelope.occurredAt());
                }
            }
            processedCounter.increment();
        }
    }
}
