package com.loopers.interfaces.consumer;

import com.loopers.infrastructure.ranking.RankingRedisRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserActionEventConsumerTest {

    @Mock
    private RankingRedisRepository rankingRedisRepository;

    @InjectMocks
    private UserActionEventConsumer userActionEventConsumer;

    @DisplayName("handleUserActionEvents() 시,")
    @Nested
    class HandleUserActionEvents {

        @DisplayName("PRODUCT_VIEWED 이벤트를 수신하면 랭킹 ZSET 점수를 +0.1 갱신한다.")
        @Test
        void incrementsScore_whenProductViewedEventReceived() {
            // arrange
            UserActionEventConsumer.UserActionEventMessage message =
                new UserActionEventConsumer.UserActionEventMessage("PRODUCT_VIEWED", 1L, 100L, null);
            ConsumerRecord<String, UserActionEventConsumer.UserActionEventMessage> record =
                new ConsumerRecord<>("user-action-events", 0, 0L, "100", message);
            Acknowledgment acknowledgment = mock(Acknowledgment.class);

            // act
            userActionEventConsumer.handleUserActionEvents(List.of(record), acknowledgment);

            // assert
            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
            ArgumentCaptor<Long> productIdCaptor = ArgumentCaptor.forClass(Long.class);
            verify(rankingRedisRepository, times(1))
                .incrementScore(any(), productIdCaptor.capture(), scoreCaptor.capture());
            assertThat(productIdCaptor.getValue()).isEqualTo(100L);
            assertThat(scoreCaptor.getValue()).isEqualTo(0.1);
            verify(acknowledgment, times(1)).acknowledge();
        }

        @DisplayName("PRODUCT_VIEWED 이외의 이벤트 타입은 랭킹 점수를 갱신하지 않는다.")
        @Test
        void skipsScoreUpdate_whenEventTypeIsNotProductViewed() {
            // arrange
            UserActionEventConsumer.UserActionEventMessage message =
                new UserActionEventConsumer.UserActionEventMessage("ORDER_CREATED", 1L, 100L, null);
            ConsumerRecord<String, UserActionEventConsumer.UserActionEventMessage> record =
                new ConsumerRecord<>("user-action-events", 0, 0L, "100", message);
            Acknowledgment acknowledgment = mock(Acknowledgment.class);

            // act
            userActionEventConsumer.handleUserActionEvents(List.of(record), acknowledgment);

            // assert
            verify(rankingRedisRepository, never()).incrementScore(any(), anyLong(), anyDouble());
            verify(acknowledgment, times(1)).acknowledge();
        }

        @DisplayName("메시지 값이 null이면 처리를 건너뛰고 ack한다.")
        @Test
        void skipsProcessing_whenMessageIsNull() {
            // arrange
            ConsumerRecord<String, UserActionEventConsumer.UserActionEventMessage> record =
                new ConsumerRecord<>("user-action-events", 0, 0L, "100", null);
            Acknowledgment acknowledgment = mock(Acknowledgment.class);

            // act
            userActionEventConsumer.handleUserActionEvents(List.of(record), acknowledgment);

            // assert
            verify(rankingRedisRepository, never()).incrementScore(any(), anyLong(), anyDouble());
            verify(acknowledgment, times(1)).acknowledge();
        }

        @DisplayName("여러 이벤트를 배치로 수신하면, PRODUCT_VIEWED 이벤트 수만큼 점수를 갱신한다.")
        @Test
        void incrementsScore_forEachProductViewedEventInBatch() {
            // arrange
            UserActionEventConsumer.UserActionEventMessage viewed1 =
                new UserActionEventConsumer.UserActionEventMessage("PRODUCT_VIEWED", 1L, 100L, null);
            UserActionEventConsumer.UserActionEventMessage viewed2 =
                new UserActionEventConsumer.UserActionEventMessage("PRODUCT_VIEWED", 2L, 200L, null);
            UserActionEventConsumer.UserActionEventMessage other =
                new UserActionEventConsumer.UserActionEventMessage("ORDER_CREATED", 3L, 300L, null);

            List<ConsumerRecord<String, UserActionEventConsumer.UserActionEventMessage>> records = List.of(
                new ConsumerRecord<>("user-action-events", 0, 0L, "100", viewed1),
                new ConsumerRecord<>("user-action-events", 0, 1L, "200", viewed2),
                new ConsumerRecord<>("user-action-events", 0, 2L, "300", other)
            );
            Acknowledgment acknowledgment = mock(Acknowledgment.class);

            // act
            userActionEventConsumer.handleUserActionEvents(records, acknowledgment);

            // assert
            verify(rankingRedisRepository, times(2)).incrementScore(any(), anyLong(), anyDouble());
            verify(acknowledgment, times(1)).acknowledge();
        }
    }
}
