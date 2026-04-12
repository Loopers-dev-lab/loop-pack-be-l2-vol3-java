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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeEventConsumerTest {

    @Mock
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Mock
    private EventHandledJpaRepository eventHandledJpaRepository;

    @Mock
    private RankingRedisRepository rankingRedisRepository;

    @InjectMocks
    private LikeEventConsumer likeEventConsumer;

    @DisplayName("handleLikeEvents() 시,")
    @Nested
    class HandleLikeEvents {

        @DisplayName("LIKED 이벤트를 수신하면 product_metrics.like_count를 +1 upsert한다.")
        @Test
        void upsertIncrease_whenLikedEventReceived() {
            // arrange
            LikeEventConsumer.LikeEventMessage message = new LikeEventConsumer.LikeEventMessage("LIKED", 100L, 1L, "evt-1");
            ConsumerRecord<String, LikeEventConsumer.LikeEventMessage> record = new ConsumerRecord<>("catalog-events", 0, 0L, "100", message);
            Acknowledgment acknowledgment = mock(Acknowledgment.class);
            when(eventHandledJpaRepository.existsByTopicAndEventId("catalog-events", "evt-1")).thenReturn(false);
            when(productMetricsJpaRepository.findByProductId(100L)).thenReturn(Optional.empty());

            // act
            likeEventConsumer.handleLikeEvents(List.of(record), acknowledgment);

            // assert
            verify(productMetricsJpaRepository, times(1)).save(
                org.mockito.ArgumentMatchers.argThat(m -> m.getProductId().equals(100L) && m.getLikeCount() == 1)
            );
            verify(acknowledgment, times(1)).acknowledge();
        }

        @DisplayName("UNLIKED 이벤트를 수신하면 product_metrics.like_count를 -1 upsert한다.")
        @Test
        void upsertDecrease_whenUnlikedEventReceived() {
            // arrange
            LikeEventConsumer.LikeEventMessage message = new LikeEventConsumer.LikeEventMessage("UNLIKED", 100L, 1L, "evt-2");
            ConsumerRecord<String, LikeEventConsumer.LikeEventMessage> record = new ConsumerRecord<>("catalog-events", 0, 0L, "100", message);
            Acknowledgment acknowledgment = mock(Acknowledgment.class);
            ProductMetrics existing = new ProductMetrics(100L, 3);
            when(eventHandledJpaRepository.existsByTopicAndEventId("catalog-events", "evt-2")).thenReturn(false);
            when(productMetricsJpaRepository.findByProductId(100L)).thenReturn(Optional.of(existing));

            // act
            likeEventConsumer.handleLikeEvents(List.of(record), acknowledgment);

            // assert
            verify(productMetricsJpaRepository, times(1)).save(
                org.mockito.ArgumentMatchers.argThat(m -> m.getProductId().equals(100L) && m.getLikeCount() == 2)
            );
            verify(acknowledgment, times(1)).acknowledge();
        }

        @DisplayName("동일한 eventId가 이미 처리되었으면, product_metrics를 갱신하지 않는다.")
        @Test
        void skipsProcessing_whenEventAlreadyHandled() {
            // arrange
            LikeEventConsumer.LikeEventMessage message = new LikeEventConsumer.LikeEventMessage("LIKED", 100L, 1L, "dup-evt");
            ConsumerRecord<String, LikeEventConsumer.LikeEventMessage> record = new ConsumerRecord<>("catalog-events", 0, 0L, "100", message);
            Acknowledgment acknowledgment = mock(Acknowledgment.class);
            when(eventHandledJpaRepository.existsByTopicAndEventId("catalog-events", "dup-evt")).thenReturn(true);

            // act
            likeEventConsumer.handleLikeEvents(List.of(record), acknowledgment);

            // assert
            verify(productMetricsJpaRepository, never()).save(any());
            verify(acknowledgment, times(1)).acknowledge();
        }

        @DisplayName("LIKED 이벤트를 수신하면 랭킹 ZSET 점수를 +0.2 갱신한다.")
        @Test
        void incrementsRankingScore_whenLikedEventReceived() {
            // arrange
            LikeEventConsumer.LikeEventMessage message = new LikeEventConsumer.LikeEventMessage("LIKED", 100L, 1L, "evt-r1");
            ConsumerRecord<String, LikeEventConsumer.LikeEventMessage> record = new ConsumerRecord<>("catalog-events", 0, 0L, "100", message);
            Acknowledgment acknowledgment = mock(Acknowledgment.class);
            when(eventHandledJpaRepository.existsByTopicAndEventId("catalog-events", "evt-r1")).thenReturn(false);
            when(productMetricsJpaRepository.findByProductId(100L)).thenReturn(Optional.empty());

            // act
            likeEventConsumer.handleLikeEvents(List.of(record), acknowledgment);

            // assert
            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
            verify(rankingRedisRepository, times(1)).incrementScore(any(), anyLong(), scoreCaptor.capture());
            assertThat(scoreCaptor.getValue()).isEqualTo(0.2);
        }

        @DisplayName("UNLIKED 이벤트를 수신하면 랭킹 ZSET 점수를 -0.2 감소한다.")
        @Test
        void decrementsRankingScore_whenUnlikedEventReceived() {
            // arrange
            LikeEventConsumer.LikeEventMessage message = new LikeEventConsumer.LikeEventMessage("UNLIKED", 100L, 1L, "evt-r2");
            ConsumerRecord<String, LikeEventConsumer.LikeEventMessage> record = new ConsumerRecord<>("catalog-events", 0, 0L, "100", message);
            Acknowledgment acknowledgment = mock(Acknowledgment.class);
            ProductMetrics existing = new ProductMetrics(100L, 3);
            when(eventHandledJpaRepository.existsByTopicAndEventId("catalog-events", "evt-r2")).thenReturn(false);
            when(productMetricsJpaRepository.findByProductId(100L)).thenReturn(Optional.of(existing));

            // act
            likeEventConsumer.handleLikeEvents(List.of(record), acknowledgment);

            // assert
            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
            verify(rankingRedisRepository, times(1)).incrementScore(any(), anyLong(), scoreCaptor.capture());
            assertThat(scoreCaptor.getValue()).isEqualTo(-0.2);
        }

        @DisplayName("알 수 없는 이벤트 타입은 무시하고 ack한다.")
        @Test
        void ignoresUnknownEventType_andAcknowledges() {
            // arrange
            LikeEventConsumer.LikeEventMessage message = new LikeEventConsumer.LikeEventMessage("UNKNOWN", 100L, 1L, "evt-3");
            ConsumerRecord<String, LikeEventConsumer.LikeEventMessage> record = new ConsumerRecord<>("catalog-events", 0, 0L, "100", message);
            Acknowledgment acknowledgment = mock(Acknowledgment.class);
            when(eventHandledJpaRepository.existsByTopicAndEventId(anyString(), anyString())).thenReturn(false);

            // act
            likeEventConsumer.handleLikeEvents(List.of(record), acknowledgment);

            // assert
            verify(productMetricsJpaRepository, never()).save(any());
            verify(acknowledgment, times(1)).acknowledge();
        }
    }
}
