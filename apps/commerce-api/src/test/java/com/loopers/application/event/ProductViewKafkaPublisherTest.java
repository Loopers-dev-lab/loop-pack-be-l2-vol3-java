package com.loopers.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.ProductViewedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProductViewKafkaPublisherTest {

    private ProductViewKafkaPublisher publisher;
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new ProductViewKafkaPublisher(kafkaTemplate, new ObjectMapper());
    }

    @Nested
    @DisplayName("ProductViewedEvent 처리")
    class HandleProductViewed {

        @DisplayName("catalog-events 토픽으로 Kafka 메시지를 발행한다")
        @Test
        void sendsKafkaMessage() {
            publisher.handle(new ProductViewedEvent(100L, 1L));

            verify(kafkaTemplate).send(eq("catalog-events"), eq("100"), anyString());
        }

        @DisplayName("productId를 Kafka 메시지 키로 사용한다")
        @Test
        void usesProductIdAsKey() {
            publisher.handle(new ProductViewedEvent(42L, 5L));

            verify(kafkaTemplate).send(eq("catalog-events"), eq("42"), anyString());
        }
    }
}
