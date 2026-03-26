package com.loopers.interfaces.consumer;

import com.loopers.domain.eventhandled.EventHandledService;
import com.loopers.domain.metrics.CatalogEventMessage;
import com.loopers.domain.metrics.ProductMetricsService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogEventConsumer 단위 테스트")
class CatalogEventConsumerTest {

    @Mock
    private ProductMetricsService productMetricsService;

    @Mock
    private EventHandledService eventHandledService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private CatalogEventConsumer catalogEventConsumer;

    private CatalogEventMessage createMessage(String eventId, String eventType, String aggregateId) {
        return new CatalogEventMessage(eventId, eventType, aggregateId, "{}", ZonedDateTime.now());
    }

    @Nested
    @DisplayName("consume - 카탈로그 이벤트 소비")
    class Consume {

        @Test
        @DisplayName("성공: PRODUCT_LIKED 이벤트 → likesCount 증가")
        void consume_productLiked_increasesLikes() {
            // Given
            CatalogEventMessage message = createMessage("event-1", "PRODUCT_LIKED", "100");
            given(eventHandledService.isAlreadyHandled("event-1")).willReturn(false);

            // When
            catalogEventConsumer.consume(List.of(message), acknowledgment);

            // Then
            then(productMetricsService).should().increaseLikes(100L);
            then(eventHandledService).should().markAsHandled("event-1");
            then(acknowledgment).should().acknowledge();
        }

        @Test
        @DisplayName("성공: PRODUCT_UNLIKED 이벤트 → likesCount 감소")
        void consume_productUnliked_decreasesLikes() {
            // Given
            CatalogEventMessage message = createMessage("event-2", "PRODUCT_UNLIKED", "100");
            given(eventHandledService.isAlreadyHandled("event-2")).willReturn(false);

            // When
            catalogEventConsumer.consume(List.of(message), acknowledgment);

            // Then
            then(productMetricsService).should().decreaseLikes(100L);
            then(eventHandledService).should().markAsHandled("event-2");
            then(acknowledgment).should().acknowledge();
        }

        @Test
        @DisplayName("성공: 이미 처리된 이벤트는 건너뛴다 (멱등)")
        void consume_alreadyHandled_skips() {
            // Given
            CatalogEventMessage message = createMessage("event-3", "PRODUCT_LIKED", "100");
            given(eventHandledService.isAlreadyHandled("event-3")).willReturn(true);

            // When
            catalogEventConsumer.consume(List.of(message), acknowledgment);

            // Then
            then(productMetricsService).shouldHaveNoInteractions();
            then(eventHandledService).should(never()).markAsHandled("event-3");
            then(acknowledgment).should().acknowledge();
        }

        @Test
        @DisplayName("성공: 알 수 없는 eventType은 무시하고 ACK한다")
        void consume_unknownEventType_ignoresAndAcks() {
            // Given
            CatalogEventMessage message = createMessage("event-4", "UNKNOWN_TYPE", "100");
            given(eventHandledService.isAlreadyHandled("event-4")).willReturn(false);

            // When
            catalogEventConsumer.consume(List.of(message), acknowledgment);

            // Then
            then(productMetricsService).shouldHaveNoInteractions();
            then(eventHandledService).should().markAsHandled("event-4");
            then(acknowledgment).should().acknowledge();
        }
    }
}
