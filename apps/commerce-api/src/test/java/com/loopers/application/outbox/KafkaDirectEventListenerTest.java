package com.loopers.application.outbox;

import com.loopers.application.product.event.ProductViewedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KafkaDirectEventListenerTest {

    private KafkaTemplate<Object, Object> kafkaTemplate;
    private KafkaDirectEventListener listener;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        listener = new KafkaDirectEventListener(kafkaTemplate);
    }

    @DisplayName("ProductViewedEvent 수신 시 eventId가 포함된 메시지를 Kafka에 발행한다")
    @Test
    void handleProductViewed_includesEventId() {
        Instant viewedAt = Instant.parse("2026-03-26T10:00:00Z");
        ProductViewedEvent event = new ProductViewedEvent(10L, 100L, viewedAt);

        listener.handleProductViewed(event);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("catalog.events.topic-v1"), eq("10"), captor.capture());

        Map<String, Object> message = captor.getValue();
        assertThat(message).containsKey("eventId");
        assertThat(message.get("eventId").toString()).matches("[0-9a-f]{8}-[0-9a-f]{4}-.*");
        assertThat(message.get("eventType")).isEqualTo("PRODUCT_VIEWED");
        assertThat(message.get("productId")).isEqualTo(10L);
        assertThat(message).containsKey("occurredAt");
    }

    @DisplayName("Kafka 발행 실패 시 예외가 전파되지 않는다")
    @Test
    void handleProductViewed_kafkaFailure_doesNotPropagate() {
        ProductViewedEvent event = new ProductViewedEvent(10L, 100L, Instant.now());
        doThrow(new RuntimeException("Kafka 장애")).when(kafkaTemplate).send(anyString(), anyString(), any());

        listener.handleProductViewed(event);

        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }
}
