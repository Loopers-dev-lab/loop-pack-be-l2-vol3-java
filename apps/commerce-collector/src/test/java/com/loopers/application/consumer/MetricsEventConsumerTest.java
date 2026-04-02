package com.loopers.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.event.AggregateType;
import com.loopers.event.EventEnvelope;
import com.loopers.event.EventType;
import com.loopers.fake.FakeEventHandledRepository;
import com.loopers.fake.FakeProductMetricsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MetricsEventConsumer 테스트")
class MetricsEventConsumerTest {

    private FakeEventHandledRepository eventHandledRepository;
    private FakeProductMetricsRepository productMetricsRepository;
    private ObjectMapper objectMapper;
    private MetricsEventConsumer consumer;

    @BeforeEach
    void setUp() {
        eventHandledRepository = new FakeEventHandledRepository();
        productMetricsRepository = new FakeProductMetricsRepository();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new MetricsEventConsumer(eventHandledRepository, productMetricsRepository, objectMapper);
    }

    private EventEnvelope createEnvelope(EventType eventType, AggregateType aggregateType, String aggregateId, String payload) {
        return new EventEnvelope(
            UUID.randomUUID().toString(),
            eventType.name(),
            aggregateType.name(),
            aggregateId,
            Instant.now(),
            payload
        );
    }

    @Nested
    @DisplayName("LIKE_CREATED 이벤트 처리")
    class LikeCreated {

        @Test
        @DisplayName("성공 - 좋아요 카운트 증가")
        void 좋아요_카운트_증가() {
            // Arrange
            String payload = "{\"likeId\":1,\"userId\":100,\"productId\":200}";
            EventEnvelope envelope = createEnvelope(EventType.LIKE_CREATED, AggregateType.PRODUCT, "200", payload);

            // Act
            consumer.processEvent(envelope);

            // Assert
            Optional<ProductMetrics> metrics = productMetricsRepository.findByProductId(200L);
            assertThat(metrics).isPresent();
            assertThat(metrics.get().getLikeCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("성공 - 처리 완료 기록")
        void 처리_완료_기록() {
            // Arrange
            String payload = "{\"likeId\":1,\"userId\":100,\"productId\":200}";
            EventEnvelope envelope = createEnvelope(EventType.LIKE_CREATED, AggregateType.PRODUCT, "200", payload);

            // Act
            consumer.processEvent(envelope);

            // Assert
            List<EventHandled> handled = eventHandledRepository.findAll();
            assertThat(handled).hasSize(1);
            assertThat(handled.get(0).getEventId()).isEqualTo(envelope.eventId());
        }

        @Test
        @DisplayName("멱등성 - 중복 이벤트는 무시")
        void 중복_이벤트_무시() {
            // Arrange
            String eventId = UUID.randomUUID().toString();
            String payload = "{\"likeId\":1,\"userId\":100,\"productId\":200}";
            EventEnvelope envelope = new EventEnvelope(
                eventId,
                EventType.LIKE_CREATED.name(),
                AggregateType.PRODUCT.name(),
                "200",
                Instant.now(),
                payload
            );

            // 첫 번째 처리
            consumer.processEvent(envelope);

            // Act - 동일한 이벤트 다시 처리
            consumer.processEvent(envelope);

            // Assert - 카운트는 1이어야 함
            Optional<ProductMetrics> metrics = productMetricsRepository.findByProductId(200L);
            assertThat(metrics).isPresent();
            assertThat(metrics.get().getLikeCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("LIKE_CANCELED 이벤트 처리")
    class LikeCanceled {

        @Test
        @DisplayName("성공 - 좋아요 카운트 감소")
        void 좋아요_카운트_감소() {
            // Arrange - 먼저 좋아요 추가
            String createPayload = "{\"likeId\":1,\"userId\":100,\"productId\":200}";
            EventEnvelope createEnvelope = createEnvelope(EventType.LIKE_CREATED, AggregateType.PRODUCT, "200", createPayload);
            consumer.processEvent(createEnvelope);

            // Act - 좋아요 취소
            String cancelPayload = "{\"likeId\":1,\"userId\":100,\"productId\":200}";
            EventEnvelope cancelEnvelope = createEnvelope(EventType.LIKE_CANCELED, AggregateType.PRODUCT, "200", cancelPayload);
            consumer.processEvent(cancelEnvelope);

            // Assert
            Optional<ProductMetrics> metrics = productMetricsRepository.findByProductId(200L);
            assertThat(metrics).isPresent();
            assertThat(metrics.get().getLikeCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("PRODUCT_VIEWED 이벤트 처리")
    class ProductViewed {

        @Test
        @DisplayName("성공 - 조회 카운트 증가")
        void 조회_카운트_증가() {
            // Arrange
            String payload = "{\"userId\":100,\"productId\":200}";
            EventEnvelope envelope = createEnvelope(EventType.PRODUCT_VIEWED, AggregateType.PRODUCT, "200", payload);

            // Act
            consumer.processEvent(envelope);

            // Assert
            Optional<ProductMetrics> metrics = productMetricsRepository.findByProductId(200L);
            assertThat(metrics).isPresent();
            assertThat(metrics.get().getViewCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("ORDER_COMPLETED 이벤트 처리")
    class OrderCompleted {

        @Test
        @DisplayName("성공 - 주문 카운트 및 금액 증가")
        void 주문_카운트_금액_증가() {
            // Arrange
            String payload = "{\"orderId\":1,\"userId\":100,\"productId\":200,\"quantity\":3,\"totalAmount\":30000}";
            EventEnvelope envelope = createEnvelope(EventType.ORDER_COMPLETED, AggregateType.ORDER, "1", payload);

            // Act
            consumer.processEvent(envelope);

            // Assert
            Optional<ProductMetrics> metrics = productMetricsRepository.findByProductId(200L);
            assertThat(metrics).isPresent();
            assertThat(metrics.get().getOrderCount()).isEqualTo(3);
            assertThat(metrics.get().getOrderTotalAmount()).isEqualTo(30000);
        }
    }
}
