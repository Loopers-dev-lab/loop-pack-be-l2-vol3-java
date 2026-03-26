package com.loopers.interfaces.consumer;

import com.loopers.domain.metrics.ProductMetricsService;
import com.loopers.infrastructure.monitoring.ConsumerMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogEventProcessor 단위 테스트")
class CatalogEventProcessorTest {

    @Mock
    ProductMetricsService productMetricsService;

    @Mock
    ConsumerMetrics consumerMetrics;

    @InjectMocks
    CatalogEventProcessor catalogEventProcessor;

    private ConsumerRecord<Object, Object> createRecord(Map<String, Object> value) {
        return new ConsumerRecord<>("catalog-events", 0, 0L, "key", value);
    }

    @Test
    @DisplayName("PRODUCT_LIKED 이벤트 수신 시 incrementLikeCount가 호출된다")
    void process_ProductLiked_ShouldCallIncrementLikeCount() {
        Map<String, Object> message = Map.of(
                "eventType", "PRODUCT_LIKED",
                "productId", 100L
        );

        catalogEventProcessor.process(createRecord(message));

        verify(productMetricsService).incrementLikeCount(100L);
    }

    @Test
    @DisplayName("PRODUCT_VIEWED 이벤트 수신 시 incrementViewCount가 호출된다")
    void process_ProductViewed_ShouldCallIncrementViewCount() {
        Map<String, Object> message = Map.of(
                "eventType", "PRODUCT_VIEWED",
                "productId", 200L
        );

        catalogEventProcessor.process(createRecord(message));

        verify(productMetricsService).incrementViewCount(200L);
    }

    @Test
    @DisplayName("PRODUCT_UNLIKED 이벤트 수신 시 decrementLikeCount가 호출된다")
    void process_ProductUnliked_ShouldCallDecrementLikeCount() {
        Map<String, Object> message = Map.of(
                "eventType", "PRODUCT_UNLIKED",
                "productId", 300L
        );

        catalogEventProcessor.process(createRecord(message));

        verify(productMetricsService).decrementLikeCount(300L);
    }

    @Test
    @DisplayName("알 수 없는 eventType은 메트릭스 서비스를 호출하지 않는다")
    void process_UnknownEventType_ShouldNotCallMetricsService() {
        Map<String, Object> message = Map.of(
                "eventType", "UNKNOWN_EVENT",
                "productId", 100L
        );

        catalogEventProcessor.process(createRecord(message));

        verifyNoInteractions(productMetricsService);
    }

    @Test
    @DisplayName("메시지가 Map 타입이 아니면 무시한다")
    void process_NonMapValue_ShouldSkip() {
        ConsumerRecord<Object, Object> record =
                new ConsumerRecord<>("catalog-events", 0, 0L, "key", "not-a-map");

        catalogEventProcessor.process(record);

        verifyNoInteractions(productMetricsService);
    }
}
