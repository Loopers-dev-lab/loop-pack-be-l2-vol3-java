package com.loopers.interfaces.consumer;

import com.loopers.domain.idempotency.EventHandledModel;
import com.loopers.domain.idempotency.EventHandledRepository;
import com.loopers.domain.idempotency.EventLogModel;
import com.loopers.domain.idempotency.EventLogRepository;
import com.loopers.domain.metrics.ProductMetricsService;
import com.loopers.infrastructure.monitoring.ConsumerMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventProcessor 단위 테스트")
class OrderEventProcessorTest {

    @Mock
    EventHandledRepository eventHandledRepository;

    @Mock
    EventLogRepository eventLogRepository;

    @Mock
    ProductMetricsService productMetricsService;

    @Mock
    ConsumerMetrics consumerMetrics;

    @InjectMocks
    OrderEventProcessor orderEventProcessor;

    private ConsumerRecord<Object, Object> createRecord(Map<String, Object> envelope) {
        return new ConsumerRecord<>("order-events", 0, 0L, "order-1", envelope);
    }

    @Test
    @DisplayName("ORDER_CREATED 이벤트 수신 시 incrementOrderCount가 호출된다")
    void process_OrderCreated_ShouldCallIncrementOrderCount() {
        Map<String, Object> item = Map.of("productId", 100L, "finalAmount", 50000);
        Map<String, Object> payload = Map.of("items", List.of(item));
        Map<String, Object> envelope = Map.of(
                "eventId", 1L,
                "eventType", "ORDER_CREATED",
                "payload", payload
        );
        when(eventHandledRepository.existsById(1L)).thenReturn(false);

        orderEventProcessor.process(createRecord(envelope));

        verify(productMetricsService).incrementOrderCount(100L, 50000L);
        verify(eventHandledRepository).save(any(EventHandledModel.class));
        verify(eventLogRepository).save(any(EventLogModel.class));
    }

    @Test
    @DisplayName("ORDER_CANCELLED 이벤트 수신 시 지표 갱신 없이 정상 처리된다")
    void process_OrderCancelled_ShouldProcessWithoutMetrics() {
        Map<String, Object> envelope = Map.of(
                "eventId", 2L,
                "eventType", "ORDER_CANCELLED",
                "payload", Map.of()
        );
        when(eventHandledRepository.existsById(2L)).thenReturn(false);

        orderEventProcessor.process(createRecord(envelope));

        verify(productMetricsService, never()).incrementOrderCount(anyLong(), anyLong());
        verify(eventHandledRepository).save(any(EventHandledModel.class));
    }

    @Test
    @DisplayName("이미 처리된 이벤트는 skip하고 메트릭스 서비스를 호출하지 않는다")
    void process_DuplicateEvent_ShouldSkip() {
        Map<String, Object> envelope = Map.of(
                "eventId", 3L,
                "eventType", "ORDER_CREATED",
                "payload", Map.of("items", List.of())
        );
        when(eventHandledRepository.existsById(3L)).thenReturn(true);

        orderEventProcessor.process(createRecord(envelope));

        verify(productMetricsService, never()).incrementOrderCount(anyLong(), anyLong());
        verify(eventHandledRepository, never()).save(any(EventHandledModel.class));
        // skip 시 eventLog에 skipped 기록
        verify(eventLogRepository).save(any(EventLogModel.class));
    }
}
