package com.loopers.application.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.event.OrderCompletedEvent;
import com.loopers.application.event.ProductLikedEvent;
import com.loopers.application.event.ProductUnlikedEvent;
import com.loopers.application.event.ProductViewedEvent;
import com.loopers.domain.outbox.OutboxEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.mockito.ArgumentCaptor;

@DisplayName("OutboxEventRecorder 단위 테스트")
@ExtendWith(MockitoExtension.class)
class OutboxEventRecorderTest {

    @InjectMocks
    private OutboxEventRecorder recorder;

    @Mock
    private OutboxEventService outboxEventService;

    @Spy
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("ProductLikedEvent 수신 시 PRODUCT 타입으로 outbox에 기록한다")
    void recordsProductLikedEvent() {
        // Arrange
        ProductLikedEvent event = new ProductLikedEvent(100L);

        // Act
        recorder.handleProductLiked(event);

        // Assert
        verify(outboxEventService).recordEvent(
            "PRODUCT", "100", "PRODUCT_LIKED", "{\"productId\":100}"
        );
    }

    @Test
    @DisplayName("ProductUnlikedEvent 수신 시 PRODUCT 타입으로 outbox에 기록한다")
    void recordsProductUnlikedEvent() {
        // Arrange
        ProductUnlikedEvent event = new ProductUnlikedEvent(200L);

        // Act
        recorder.handleProductUnliked(event);

        // Assert
        verify(outboxEventService).recordEvent(
            "PRODUCT", "200", "PRODUCT_UNLIKED", "{\"productId\":200}"
        );
    }

    @Test
    @DisplayName("ProductViewedEvent 수신 시 PRODUCT 타입으로 outbox에 기록한다")
    void recordsProductViewedEvent() {
        // Arrange
        ProductViewedEvent event = new ProductViewedEvent(300L);

        // Act
        recorder.handleProductViewed(event);

        // Assert
        verify(outboxEventService).recordEvent(
            "PRODUCT", "300", "PRODUCT_VIEWED", "{\"productId\":300}"
        );
    }

    @Test
    @DisplayName("OrderCompletedEvent 수신 시 ORDER 타입으로 outbox에 기록한다")
    void recordsOrderCompletedEvent() {
        // Arrange
        OrderCompletedEvent event = new OrderCompletedEvent(
            300L, 10L, Set.of(100L, 200L), 50000L
        );

        // Act
        recorder.handleOrderCompleted(event);

        // Assert
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxEventService).recordEvent(
            eq("ORDER"), eq("300"), eq("ORDER_COMPLETED"), payloadCaptor.capture()
        );
        String payload = payloadCaptor.getValue();
        assertThat(payload).contains("\"orderId\":300");
        assertThat(payload).contains("\"memberId\":10");
        assertThat(payload).contains("\"totalAmount\":50000");
        assertThat(payload).contains("\"productIds\":");
        assertThat(payload).contains("100");
        assertThat(payload).contains("200");
    }
}
