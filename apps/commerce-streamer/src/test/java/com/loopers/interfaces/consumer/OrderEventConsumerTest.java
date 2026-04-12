package com.loopers.interfaces.consumer;

import com.loopers.domain.product.ProductMetrics;
import com.loopers.infrastructure.event.EventHandledJpaRepository;
import com.loopers.infrastructure.product.ProductMetricsJpaRepository;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Mock
    private EventHandledJpaRepository eventHandledJpaRepository;

    @Mock
    private RankingRedisRepository rankingRedisRepository;

    @InjectMocks
    private OrderEventConsumer orderEventConsumer;

    @DisplayName("handleOrderEvents() 시,")
    @Nested
    class HandleOrderEvents {

        @DisplayName("ORDER_CREATED 이벤트를 수신하면 product_metrics.order_count를 +1 upsert한다.")
        @Test
        void upsertIncrease_whenOrderCreatedEventReceived() {
            // arrange
            OrderEventConsumer.OrderEventMessage message = new OrderEventConsumer.OrderEventMessage("ORDER_CREATED", 100L, 1L, 50000, "evt-1");
            ConsumerRecord<String, OrderEventConsumer.OrderEventMessage> record = new ConsumerRecord<>("order-events", 0, 0L, "1", message);
            Acknowledgment acknowledgment = mock(Acknowledgment.class);
            when(eventHandledJpaRepository.existsByTopicAndEventId("order-events", "evt-1")).thenReturn(false);
            when(productMetricsJpaRepository.findByProductId(100L)).thenReturn(Optional.empty());

            // act
            orderEventConsumer.handleOrderEvents(List.of(record), acknowledgment);

            // assert
            verify(productMetricsJpaRepository, times(1)).save(
                argThat(m -> m.getProductId().equals(100L) && m.getOrderCount() == 1)
            );
            verify(acknowledgment, times(1)).acknowledge();
        }

        @DisplayName("ORDER_CREATED 이벤트를 수신하면 기존 order_count에 +1한다.")
        @Test
        void upsertIncrease_whenMetricsAlreadyExists() {
            // arrange
            OrderEventConsumer.OrderEventMessage message = new OrderEventConsumer.OrderEventMessage("ORDER_CREATED", 100L, 1L, 50000, "evt-2");
            ConsumerRecord<String, OrderEventConsumer.OrderEventMessage> record = new ConsumerRecord<>("order-events", 0, 0L, "1", message);
            Acknowledgment acknowledgment = mock(Acknowledgment.class);
            ProductMetrics existing = new ProductMetrics(100L, 5, 3);
            when(eventHandledJpaRepository.existsByTopicAndEventId("order-events", "evt-2")).thenReturn(false);
            when(productMetricsJpaRepository.findByProductId(100L)).thenReturn(Optional.of(existing));

            // act
            orderEventConsumer.handleOrderEvents(List.of(record), acknowledgment);

            // assert
            verify(productMetricsJpaRepository, times(1)).save(
                argThat(m -> m.getProductId().equals(100L) && m.getOrderCount() == 4)
            );
            verify(acknowledgment, times(1)).acknowledge();
        }

        @DisplayName("동일한 eventId가 이미 처리되었으면, product_metrics를 갱신하지 않는다.")
        @Test
        void skipsProcessing_whenEventAlreadyHandled() {
            // arrange
            OrderEventConsumer.OrderEventMessage message = new OrderEventConsumer.OrderEventMessage("ORDER_CREATED", 100L, 1L, 50000, "dup-evt");
            ConsumerRecord<String, OrderEventConsumer.OrderEventMessage> record = new ConsumerRecord<>("order-events", 0, 0L, "1", message);
            Acknowledgment acknowledgment = mock(Acknowledgment.class);
            when(eventHandledJpaRepository.existsByTopicAndEventId("order-events", "dup-evt")).thenReturn(true);

            // act
            orderEventConsumer.handleOrderEvents(List.of(record), acknowledgment);

            // assert
            verify(productMetricsJpaRepository, never()).save(any());
            verify(acknowledgment, times(1)).acknowledge();
        }

        @DisplayName("ORDER_CREATED 이벤트를 수신하면 금액 기반으로 랭킹 ZSET 점수를 갱신한다.")
        @Test
        void incrementsRankingScore_whenOrderCreatedEventReceived() {
            // arrange
            int finalAmount = 50000;
            OrderEventConsumer.OrderEventMessage message =
                new OrderEventConsumer.OrderEventMessage("ORDER_CREATED", 100L, 1L, finalAmount, "evt-r1");
            ConsumerRecord<String, OrderEventConsumer.OrderEventMessage> record =
                new ConsumerRecord<>("order-events", 0, 0L, "1", message);
            Acknowledgment acknowledgment = mock(Acknowledgment.class);
            when(eventHandledJpaRepository.existsByTopicAndEventId("order-events", "evt-r1")).thenReturn(false);
            when(productMetricsJpaRepository.findByProductId(100L)).thenReturn(Optional.empty());

            // act
            orderEventConsumer.handleOrderEvents(List.of(record), acknowledgment);

            // assert: score = 0.7 * log1p(50000)
            double expectedScore = 0.7 * Math.log1p(finalAmount);
            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
            verify(rankingRedisRepository, times(1)).incrementScore(any(), anyLong(), scoreCaptor.capture());
            assertThat(scoreCaptor.getValue()).isEqualTo(expectedScore);
        }

        @DisplayName("알 수 없는 이벤트 타입은 무시하고 ack한다.")
        @Test
        void ignoresUnknownEventType_andAcknowledges() {
            // arrange
            OrderEventConsumer.OrderEventMessage message = new OrderEventConsumer.OrderEventMessage("UNKNOWN", 100L, 1L, 0, "evt-3");
            ConsumerRecord<String, OrderEventConsumer.OrderEventMessage> record = new ConsumerRecord<>("order-events", 0, 0L, "1", message);
            Acknowledgment acknowledgment = mock(Acknowledgment.class);
            when(eventHandledJpaRepository.existsByTopicAndEventId(anyString(), anyString())).thenReturn(false);

            // act
            orderEventConsumer.handleOrderEvents(List.of(record), acknowledgment);

            // assert
            verify(productMetricsJpaRepository, never()).save(any());
            verify(acknowledgment, times(1)).acknowledge();
        }
    }
}
