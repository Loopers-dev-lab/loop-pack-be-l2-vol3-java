package com.loopers.application.metrics;

import com.loopers.contract.kafka.ProductMetricsEventMessage;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.metrics.ProductMetricsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductMetricsConsumerServiceTest {

    @Test
    @DisplayName("중복 이벤트가 아니면 집계를 반영한다")
    void consume_newEvent_updatesMetrics() {
        EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
        ProductMetricsRepository productMetricsRepository = mock(ProductMetricsRepository.class);
        ProductMetricsAckPublisher productMetricsAckPublisher = mock(ProductMetricsAckPublisher.class);
        ProductMetricsConsumerService productMetricsConsumerService =
                new ProductMetricsConsumerService(eventHandledRepository, productMetricsRepository, productMetricsAckPublisher);

        ProductMetricsEventMessage message = new ProductMetricsEventMessage(
                UUID.randomUUID(),
                "LIKE_REGISTER",
                "product-1",
                1,
                0,
                0,
                1000,
                Instant.now()
        );

        when(eventHandledRepository.markHandledIfAbsent("collector", message.eventId())).thenReturn(true);

        productMetricsConsumerService.consume("collector", message);

        verify(productMetricsRepository, times(1)).upsert(message);
        verify(productMetricsAckPublisher, times(1)).publish(message.eventId(), "collector");
    }

    @Test
    @DisplayName("이미 처리된 이벤트면 집계를 건너뛴다")
    void consume_duplicateEvent_skipsMetrics() {
        EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
        ProductMetricsRepository productMetricsRepository = mock(ProductMetricsRepository.class);
        ProductMetricsAckPublisher productMetricsAckPublisher = mock(ProductMetricsAckPublisher.class);
        ProductMetricsConsumerService productMetricsConsumerService =
                new ProductMetricsConsumerService(eventHandledRepository, productMetricsRepository, productMetricsAckPublisher);

        ProductMetricsEventMessage message = new ProductMetricsEventMessage(
                UUID.randomUUID(),
                "LIKE_REGISTER",
                "product-1",
                1,
                0,
                0,
                1000,
                Instant.now()
        );

        when(eventHandledRepository.markHandledIfAbsent("collector", message.eventId())).thenReturn(false);

        productMetricsConsumerService.consume("collector", message);

        verify(productMetricsRepository, never()).upsert(message);
        verify(productMetricsAckPublisher, never()).publish(message.eventId(), "collector");
    }
}
