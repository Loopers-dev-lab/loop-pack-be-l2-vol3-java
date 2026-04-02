package com.loopers.application.event.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.event.LikeCanceledEvent;
import com.loopers.application.event.LikeCreatedEvent;
import com.loopers.application.event.OrderCompletedEvent;
import com.loopers.application.event.ProductViewedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.event.AggregateType;
import com.loopers.event.EventType;
import com.loopers.event.KafkaTopics;
import com.loopers.fake.FakeOutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutboxEventListener 테스트")
class OutboxEventListenerTest {

    private FakeOutboxEventRepository outboxEventRepository;
    private ObjectMapper objectMapper;
    private OutboxEventListener listener;

    @BeforeEach
    void setUp() {
        outboxEventRepository = new FakeOutboxEventRepository();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        listener = new OutboxEventListener(outboxEventRepository, objectMapper);
    }

    @Nested
    @DisplayName("LikeCreatedEvent 처리")
    class LikeCreated {

        @Test
        @DisplayName("성공 - Outbox에 이벤트 저장")
        void 좋아요_생성_이벤트_저장() {
            // Arrange
            LikeCreatedEvent event = LikeCreatedEvent.of(1L, 100L, 200L);

            // Act
            listener.handleLikeCreatedEvent(event);

            // Assert
            List<OutboxEvent> events = outboxEventRepository.findAll();
            assertThat(events).hasSize(1);

            OutboxEvent outboxEvent = events.get(0);
            assertThat(outboxEvent.getEventType()).isEqualTo(EventType.LIKE_CREATED.name());
            assertThat(outboxEvent.getAggregateType()).isEqualTo(AggregateType.PRODUCT.name());
            assertThat(outboxEvent.getAggregateId()).isEqualTo("200");
            assertThat(outboxEvent.getTopic()).isEqualTo(KafkaTopics.CATALOG_EVENTS);
            assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(outboxEvent.getPayload()).contains("100");
            assertThat(outboxEvent.getPayload()).contains("200");
        }
    }

    @Nested
    @DisplayName("LikeCanceledEvent 처리")
    class LikeCanceled {

        @Test
        @DisplayName("성공 - Outbox에 이벤트 저장")
        void 좋아요_취소_이벤트_저장() {
            // Arrange
            LikeCanceledEvent event = LikeCanceledEvent.of(1L, 100L, 200L);

            // Act
            listener.handleLikeCanceledEvent(event);

            // Assert
            List<OutboxEvent> events = outboxEventRepository.findAll();
            assertThat(events).hasSize(1);

            OutboxEvent outboxEvent = events.get(0);
            assertThat(outboxEvent.getEventType()).isEqualTo(EventType.LIKE_CANCELED.name());
            assertThat(outboxEvent.getAggregateType()).isEqualTo(AggregateType.PRODUCT.name());
            assertThat(outboxEvent.getAggregateId()).isEqualTo("200");
            assertThat(outboxEvent.getTopic()).isEqualTo(KafkaTopics.CATALOG_EVENTS);
            assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("OrderCompletedEvent 처리")
    class OrderCompleted {

        @Test
        @DisplayName("성공 - Outbox에 이벤트 저장")
        void 주문_완료_이벤트_저장() {
            // Arrange
            OrderCompletedEvent event = OrderCompletedEvent.of(1L, 100L, 200L, 2, 50000L);

            // Act
            listener.handleOrderCompletedEvent(event);

            // Assert
            List<OutboxEvent> events = outboxEventRepository.findAll();
            assertThat(events).hasSize(1);

            OutboxEvent outboxEvent = events.get(0);
            assertThat(outboxEvent.getEventType()).isEqualTo(EventType.ORDER_COMPLETED.name());
            assertThat(outboxEvent.getAggregateType()).isEqualTo(AggregateType.ORDER.name());
            assertThat(outboxEvent.getAggregateId()).isEqualTo("1");
            assertThat(outboxEvent.getTopic()).isEqualTo(KafkaTopics.ORDER_EVENTS);
            assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("ProductViewedEvent 처리")
    class ProductViewed {

        @Test
        @DisplayName("성공 - Outbox에 이벤트 저장")
        void 상품_조회_이벤트_저장() {
            // Arrange
            ProductViewedEvent event = ProductViewedEvent.of(100L, 200L);

            // Act
            listener.handleProductViewedEvent(event);

            // Assert
            List<OutboxEvent> events = outboxEventRepository.findAll();
            assertThat(events).hasSize(1);

            OutboxEvent outboxEvent = events.get(0);
            assertThat(outboxEvent.getEventType()).isEqualTo(EventType.PRODUCT_VIEWED.name());
            assertThat(outboxEvent.getAggregateType()).isEqualTo(AggregateType.PRODUCT.name());
            assertThat(outboxEvent.getAggregateId()).isEqualTo("200");
            assertThat(outboxEvent.getTopic()).isEqualTo(KafkaTopics.CATALOG_EVENTS);
            assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        }
    }
}
