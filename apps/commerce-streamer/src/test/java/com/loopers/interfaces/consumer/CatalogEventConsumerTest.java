package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.infrastructure.outbox.EventHandledJpaRepository;
import com.loopers.infrastructure.ranking.RankingCacheService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CatalogEventConsumerTest {

    @Mock
    private EventHandledJpaRepository eventHandledRepository;

    @Mock
    private ProductMetricsJpaRepository productMetricsRepository;

    @Mock
    private RankingCacheService rankingCacheService;

    private CatalogEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new CatalogEventConsumer(
            eventHandledRepository,
            productMetricsRepository,
            rankingCacheService,
            objectMapper
        );
    }

    private ConsumerRecord<Object, Object> record(String payload) {
        return new ConsumerRecord<>("catalog-events", 0, 0L, null, payload.getBytes());
    }

    private String viewedPayload(Long productId) {
        return """
            {
              "eventId": "%s",
              "eventType": "PRODUCT_VIEWED",
              "occurredAt": "%s",
              "data": { "productId": %d }
            }
            """.formatted(UUID.randomUUID(), ZonedDateTime.now(), productId);
    }

    private String likedPayload(Long productId) {
        return """
            {
              "eventId": "%s",
              "eventType": "LIKE_ADDED",
              "occurredAt": "%s",
              "data": { "productId": %d }
            }
            """.formatted(UUID.randomUUID(), ZonedDateTime.now(), productId);
    }

    private String orderedPayload(Long productId, int price, int quantity) {
        return """
            {
              "eventId": "%s",
              "eventType": "PRODUCT_ORDERED",
              "occurredAt": "%s",
              "data": { "productId": %d, "price": %d, "quantity": %d }
            }
            """.formatted(UUID.randomUUID(), ZonedDateTime.now(), productId, price, quantity);
    }

    @DisplayName("가중치 계산")
    @Nested
    class WeightCalculation {

        @BeforeEach
        void mockCommon() {
            given(eventHandledRepository.existsById(anyString())).willReturn(false);
            given(productMetricsRepository.findById(1L)).willReturn(Optional.of(mock(com.loopers.domain.metrics.ProductMetrics.class)));
        }

        @DisplayName("조회 이벤트 발생 시 ZSET에 0.1 점수가 적립된다.")
        @Test
        void incrementsViewScore() {
            // Arrange
            var ack = mock(org.springframework.kafka.support.Acknowledgment.class);

            // Act
            consumer.consume(List.of(record(viewedPayload(1L))), ack);

            // Assert
            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
            verify(rankingCacheService).incrementScore(anyString(), org.mockito.ArgumentMatchers.eq(1L), scoreCaptor.capture());
            assertThat(scoreCaptor.getValue()).isEqualTo(0.1);
        }

        @DisplayName("좋아요 이벤트 발생 시 ZSET에 0.2 점수가 적립된다.")
        @Test
        void incrementsLikeScore() {
            // Arrange
            var ack = mock(org.springframework.kafka.support.Acknowledgment.class);

            // Act
            consumer.consume(List.of(record(likedPayload(1L))), ack);

            // Assert
            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
            verify(rankingCacheService).incrementScore(anyString(), org.mockito.ArgumentMatchers.eq(1L), scoreCaptor.capture());
            assertThat(scoreCaptor.getValue()).isEqualTo(0.2);
        }

        @DisplayName("주문 이벤트 발생 시 ZSET에 0.7×price×quantity 점수가 적립된다.")
        @Test
        void incrementsOrderScore() {
            // Arrange
            var ack = mock(org.springframework.kafka.support.Acknowledgment.class);
            int price = 10000;
            int quantity = 2;
            double expectedScore = 0.7 * price * quantity; // 14000.0

            // Act
            consumer.consume(List.of(record(orderedPayload(1L, price, quantity))), ack);

            // Assert
            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
            verify(rankingCacheService).incrementScore(anyString(), org.mockito.ArgumentMatchers.eq(1L), scoreCaptor.capture());
            assertThat(scoreCaptor.getValue()).isEqualTo(expectedScore);
        }

        @DisplayName("주문 1건(1,000원×1개) 점수가 좋아요 3건 점수보다 크다.")
        @Test
        void orderScoreIsGreaterThanThreeLikes() {
            // Arrange
            double likeScore = 0.2 * 3;      // 좋아요 3건 = 0.6
            double orderScore = 0.7 * 1000 * 1; // 주문 1건(1000원×1개) = 700.0

            // Assert
            assertThat(orderScore).isGreaterThan(likeScore);
        }
    }
}
