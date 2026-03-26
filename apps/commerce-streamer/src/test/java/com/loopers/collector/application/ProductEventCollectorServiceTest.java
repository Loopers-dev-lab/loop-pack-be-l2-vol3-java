package com.loopers.collector.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.infrastructure.collector.EventHandledJpaRepository;
import com.loopers.infrastructure.collector.ProductMetricsJpaRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductEventCollectorServiceTest {

    @Mock
    private EventHandledJpaRepository eventHandledJpaRepository;

    @Mock
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    private SimpleMeterRegistry meterRegistry;

    private ProductEventCollectorService collectorService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        collectorService = new ProductEventCollectorService(
                eventHandledJpaRepository,
                productMetricsJpaRepository,
                new ObjectMapper().findAndRegisterModules(),
                meterRegistry
        );
    }

    @Test
    @DisplayName("신규 PRODUCT_LIKE_CHANGED 이벤트는 event_handled 저장 후 like delta를 반영한다.")
    void process_whenNewLikeEvent_shouldRecordHandledAndUpdateMetrics() {
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "product-events",
                0,
                5L,
                "101",
                envelopeJson("evt-1", "PRODUCT_LIKE_CHANGED", "2026-03-26T00:00:00Z", 101L, "LIKED").getBytes()
        );

        collectorService.process(record);

        verify(eventHandledJpaRepository).saveAndFlush(any());
        verify(productMetricsJpaRepository).applyLikeDeltaIfNewer(
                eq(101L),
                eq(1L),
                eq(Instant.parse("2026-03-26T00:00:00Z"))
        );
        verifyMetricCount("kafka.collector.events.processed", 1.0);
    }

    @Test
    @DisplayName("이미 처리된 event_id(PK 충돌)면 metrics 갱신 없이 스킵한다.")
    void process_whenDuplicateEvent_shouldSkipMetricsUpdate() {
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "product-events",
                1,
                10L,
                "101",
                envelopeJson("evt-dup", "PRODUCT_LIKE_CHANGED", "2026-03-26T00:00:00Z", 101L, "UNLIKED").getBytes()
        );
        doThrow(new DataIntegrityViolationException("duplicate")).when(eventHandledJpaRepository).saveAndFlush(any());

        collectorService.process(record);

        verify(eventHandledJpaRepository).saveAndFlush(any());
        verify(productMetricsJpaRepository, never()).applyLikeDeltaIfNewer(any(), any(Long.class), any());
        verifyMetricCount("kafka.collector.events.duplicate", 1.0);
    }

    @Test
    @DisplayName("이벤트 파싱 실패 시 실패 메트릭을 증가시킨다.")
    void process_whenInvalidEnvelope_shouldIncreaseFailedMetric() {
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "product-events",
                0,
                1L,
                "101",
                "{not-json".getBytes()
        );

        try {
            collectorService.process(record);
        } catch (IllegalArgumentException ignored) {
        }

        verifyMetricCount("kafka.collector.events.failed", 1.0);
    }

    private void verifyMetricCount(String metricName, double expected) {
        assert meterRegistry.find(metricName).counter() != null;
        assertThat(meterRegistry.find(metricName).counter().count()).isEqualTo(expected);
    }

    private static String envelopeJson(String eventId, String eventType, String occurredAt, Long productId, String action) {
        return "{"
                + "\"eventId\":\"" + eventId + "\","
                + "\"eventType\":\"" + eventType + "\","
                + "\"occurredAt\":\"" + occurredAt + "\","
                + "\"partitionKey\":\"" + productId + "\","
                + "\"data\":{"
                + "\"productId\":" + productId + ","
                + "\"action\":\"" + action + "\""
                + "}"
                + "}";
    }
}
