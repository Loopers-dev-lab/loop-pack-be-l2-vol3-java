package com.loopers.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.event.AggregateType;
import com.loopers.event.EventEnvelope;
import com.loopers.event.EventType;
import com.loopers.fake.FakeEventHandledRepository;
import com.loopers.fake.FakeProductMetricsRepository;
import com.loopers.fake.FakeRankingBatchProcessor;
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
    private FakeRankingBatchProcessor rankingBatchProcessor;
    private MetricsEventConsumer consumer;

    @BeforeEach
    void setUp() {
        eventHandledRepository = new FakeEventHandledRepository();
        productMetricsRepository = new FakeProductMetricsRepository();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        rankingBatchProcessor = new FakeRankingBatchProcessor();
        consumer = new MetricsEventConsumer(
            eventHandledRepository,
            productMetricsRepository,
            objectMapper,
            rankingBatchProcessor
        );
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

    @Nested
    @DisplayName("배치 이벤트 처리")
    class BatchProcessing {

        @Test
        @DisplayName("성공 - 여러 이벤트를 배치로 처리하면 product_metrics는 개별 처리된다")
        void 배치_처리_메트릭스_개별_처리() {
            // Arrange
            List<EventEnvelope> envelopes = List.of(
                createEnvelope(EventType.PRODUCT_VIEWED, AggregateType.PRODUCT, "100", "{}"),
                createEnvelope(EventType.PRODUCT_VIEWED, AggregateType.PRODUCT, "100", "{}"),
                createEnvelope(EventType.PRODUCT_VIEWED, AggregateType.PRODUCT, "200", "{}"),
                createEnvelope(EventType.LIKE_CREATED, AggregateType.PRODUCT, "100", "{}")
            );

            // Act
            consumer.processEventsBatch(envelopes);

            // Assert - product_metrics는 개별 처리됨
            Optional<ProductMetrics> metrics100 = productMetricsRepository.findByProductId(100L);
            Optional<ProductMetrics> metrics200 = productMetricsRepository.findByProductId(200L);

            assertThat(metrics100).isPresent();
            assertThat(metrics100.get().getViewCount()).isEqualTo(2);
            assertThat(metrics100.get().getLikeCount()).isEqualTo(1);

            assertThat(metrics200).isPresent();
            assertThat(metrics200.get().getViewCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("성공 - 배치 처리 시 랭킹은 RankingBatchProcessor로 일괄 처리된다")
        void 배치_처리_랭킹_일괄_처리() {
            // Arrange
            List<EventEnvelope> envelopes = List.of(
                createEnvelope(EventType.PRODUCT_VIEWED, AggregateType.PRODUCT, "100", "{}"),
                createEnvelope(EventType.PRODUCT_VIEWED, AggregateType.PRODUCT, "100", "{}"),
                createEnvelope(EventType.PRODUCT_VIEWED, AggregateType.PRODUCT, "200", "{}"),
                createEnvelope(EventType.LIKE_CREATED, AggregateType.PRODUCT, "100", "{}")
            );

            // Act
            consumer.processEventsBatch(envelopes);

            // Assert - RankingBatchProcessor가 1회만 호출됨
            assertThat(rankingBatchProcessor.getBatchCallCount()).isEqualTo(1);

            // Assert - 배치 호출에 4개 엔트리가 전달됨
            List<List<com.loopers.application.ranking.RankingBatchProcessor.ScoreEntry>> history =
                rankingBatchProcessor.getBatchCallHistory();
            assertThat(history.get(0)).hasSize(4);
        }

        @Test
        @DisplayName("성공 - 배치 내 중복 이벤트는 멱등성 체크로 스킵된다")
        void 배치_내_중복_이벤트_스킵() {
            // Arrange
            String eventId = UUID.randomUUID().toString();
            EventEnvelope duplicateEnvelope = new EventEnvelope(
                eventId,
                EventType.PRODUCT_VIEWED.name(),
                AggregateType.PRODUCT.name(),
                "100",
                Instant.now(),
                "{}"
            );

            List<EventEnvelope> envelopes = List.of(
                duplicateEnvelope,
                duplicateEnvelope, // 동일 이벤트
                createEnvelope(EventType.PRODUCT_VIEWED, AggregateType.PRODUCT, "200", "{}")
            );

            // Act
            consumer.processEventsBatch(envelopes);

            // Assert - 중복 이벤트는 1회만 처리됨
            Optional<ProductMetrics> metrics100 = productMetricsRepository.findByProductId(100L);
            assertThat(metrics100).isPresent();
            assertThat(metrics100.get().getViewCount()).isEqualTo(1);

            // Assert - 랭킹 배치에도 중복 제외된 2개만 포함
            List<List<com.loopers.application.ranking.RankingBatchProcessor.ScoreEntry>> history =
                rankingBatchProcessor.getBatchCallHistory();
            assertThat(history.get(0)).hasSize(2);
        }

        @Test
        @DisplayName("성공 - ORDER_COMPLETED 배치 처리")
        void 배치_처리_주문_이벤트() {
            // Arrange
            List<EventEnvelope> envelopes = List.of(
                createEnvelope(EventType.ORDER_COMPLETED, AggregateType.ORDER, "1",
                    "{\"orderId\":1,\"userId\":100,\"productId\":100,\"quantity\":2,\"totalAmount\":20000}"),
                createEnvelope(EventType.ORDER_COMPLETED, AggregateType.ORDER, "2",
                    "{\"orderId\":2,\"userId\":100,\"productId\":100,\"quantity\":3,\"totalAmount\":30000}"),
                createEnvelope(EventType.ORDER_COMPLETED, AggregateType.ORDER, "3",
                    "{\"orderId\":3,\"userId\":200,\"productId\":200,\"quantity\":1,\"totalAmount\":10000}")
            );

            // Act
            consumer.processEventsBatch(envelopes);

            // Assert - product_metrics 개별 처리
            Optional<ProductMetrics> metrics100 = productMetricsRepository.findByProductId(100L);
            assertThat(metrics100).isPresent();
            assertThat(metrics100.get().getOrderCount()).isEqualTo(5); // 2 + 3
            assertThat(metrics100.get().getOrderTotalAmount()).isEqualTo(50000); // 20000 + 30000

            Optional<ProductMetrics> metrics200 = productMetricsRepository.findByProductId(200L);
            assertThat(metrics200).isPresent();
            assertThat(metrics200.get().getOrderCount()).isEqualTo(1);

            // Assert - 랭킹 배치 1회 호출, 3개 엔트리
            assertThat(rankingBatchProcessor.getBatchCallCount()).isEqualTo(1);
            assertThat(rankingBatchProcessor.getBatchCallHistory().get(0)).hasSize(3);
        }
    }
}
