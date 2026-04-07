package com.loopers.interfaces.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.ranking.RankingService;
import com.loopers.domain.ranking.RankingEvent;
import com.loopers.interfaces.consumer.support.KafkaMessageParser;

@ExtendWith(MockitoExtension.class)
class ProductRankingConsumerTest {

    @InjectMocks
    private ProductRankingConsumer productRankingConsumer;

    @Mock
    private RankingService rankingService;

    @Spy
    private final KafkaMessageParser kafkaMessageParser = new KafkaMessageParser(new ObjectMapper());

    @Mock
    private Acknowledgment acknowledgment;

    @Captor
    private ArgumentCaptor<List<RankingEvent>> eventsCaptor;

    @DisplayName("랭킹 이벤트를 소비할 때,")
    @Nested
    class ConsumeRankingEvents {

        @DisplayName("좋아요 토픽이면, Like 이벤트를 생성한다.")
        @Test
        void createsLikeEvent_whenLikedTopic() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "like-liked-v1", 0, 0, "1", "{\"eventId\":\"uuid\",\"productId\":1}");

            // act
            productRankingConsumer.consumeRankingEvents(List.of(record), acknowledgment);

            // assert
            then(rankingService).should().processBatch(eventsCaptor.capture());
            RankingEvent event = eventsCaptor.getValue().get(0);
            assertThat(event).isInstanceOf(RankingEvent.Like.class);
            assertThat(event.eventId()).isEqualTo("uuid");
            assertThat(((RankingEvent.Like) event).liked()).isTrue();
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("좋아요 취소 토픽이면, liked=false인 Like 이벤트를 생성한다.")
        @Test
        void createsUnlikeEvent_whenUnlikedTopic() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "like-unliked-v1", 0, 0, "1", "{\"eventId\":\"uuid\",\"productId\":1}");

            // act
            productRankingConsumer.consumeRankingEvents(List.of(record), acknowledgment);

            // assert
            then(rankingService).should().processBatch(eventsCaptor.capture());
            RankingEvent event = eventsCaptor.getValue().get(0);
            assertThat(((RankingEvent.Like) event).liked()).isFalse();
        }

        @DisplayName("주문 완료 토픽이면, 이벤트 단위로 Order를 생성하고 항목을 포함한다.")
        @Test
        void createsOrderEvent_withAllItems() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "order-completed-v1", 0, 0, "1",
                    "{\"eventId\":\"uuid\",\"orderId\":1,\"orderItems\":["
                            + "{\"productId\":10,\"quantity\":2,\"price\":50000},"
                            + "{\"productId\":20,\"quantity\":1,\"price\":30000}]}");

            // act
            productRankingConsumer.consumeRankingEvents(List.of(record), acknowledgment);

            // assert
            then(rankingService).should().processBatch(eventsCaptor.capture());
            List<RankingEvent> events = eventsCaptor.getValue();
            assertThat(events).hasSize(1);

            RankingEvent.Order order = (RankingEvent.Order) events.get(0);
            assertThat(order.eventId()).isEqualTo("uuid");
            assertThat(order.orderItems()).hasSize(2);
            assertThat(order.orderItems().get(0).productId()).isEqualTo(10L);
            assertThat(order.orderItems().get(0).price()).isEqualTo(50000L);
            assertThat(order.orderItems().get(0).quantity()).isEqualTo(2L);
            assertThat(order.orderItems().get(1).productId()).isEqualTo(20L);
        }

        @DisplayName("상품 조회 토픽이면, View 이벤트를 생성한다.")
        @Test
        void createsViewEvent() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "product-viewed-v1", 0, 0, "1", "{\"eventId\":\"uuid\",\"productId\":1}");

            // act
            productRankingConsumer.consumeRankingEvents(List.of(record), acknowledgment);

            // assert
            then(rankingService).should().processBatch(eventsCaptor.capture());
            RankingEvent.View view = (RankingEvent.View) eventsCaptor.getValue().get(0);
            assertThat(view.productId()).isEqualTo(1L);
        }

        @DisplayName("혼합 토픽 배치를 단일 processBatch로 처리한다.")
        @Test
        void processesMixedTopicsInSingleBatch() {
            // arrange
            List<ConsumerRecord<String, Object>> records = List.of(
                    new ConsumerRecord<>("product-viewed-v1", 0, 0, "1", "{\"eventId\":\"e1\",\"productId\":1}"),
                    new ConsumerRecord<>("like-liked-v1", 0, 1, "2", "{\"eventId\":\"e2\",\"productId\":1}"),
                    new ConsumerRecord<>("order-completed-v1", 0, 2, "3",
                            "{\"eventId\":\"e3\",\"orderId\":1,\"orderItems\":[{\"productId\":1,\"quantity\":1,\"price\":10000}]}")
            );

            // act
            productRankingConsumer.consumeRankingEvents(records, acknowledgment);

            // assert
            then(rankingService).should().processBatch(eventsCaptor.capture());
            List<RankingEvent> events = eventsCaptor.getValue();
            assertThat(events).hasSize(3);
            assertThat(events.get(0)).isInstanceOf(RankingEvent.View.class);
            assertThat(events.get(1)).isInstanceOf(RankingEvent.Like.class);
            assertThat(events.get(2)).isInstanceOf(RankingEvent.Order.class);
        }

        @DisplayName("알 수 없는 토픽이면, 이벤트를 생성하지 않는다.")
        @Test
        void createsNoEvent_whenUnknownTopic() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "unknown-topic-v1", 0, 0, "1", "{\"eventId\":\"uuid\",\"productId\":1}");

            // act
            productRankingConsumer.consumeRankingEvents(List.of(record), acknowledgment);

            // assert
            then(rankingService).should().processBatch(eventsCaptor.capture());
            assertThat(eventsCaptor.getValue()).isEmpty();
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("주문의 orderItems가 비어있으면, 이벤트를 생성하지 않는다.")
        @Test
        void createsNoEvent_whenEmptyOrderItems() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "order-completed-v1", 0, 0, "1",
                    "{\"eventId\":\"uuid\",\"orderId\":1,\"orderItems\":[]}");

            // act
            productRankingConsumer.consumeRankingEvents(List.of(record), acknowledgment);

            // assert
            then(rankingService).should().processBatch(eventsCaptor.capture());
            assertThat(eventsCaptor.getValue()).isEmpty();
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("파싱 실패 시 skip하고 나머지를 처리한다.")
        @Test
        void skipsFailedRecord() {
            // arrange
            List<ConsumerRecord<String, Object>> records = List.of(
                    new ConsumerRecord<>("like-liked-v1", 0, 0, "1", "invalid-json"),
                    new ConsumerRecord<>("product-viewed-v1", 0, 1, "2", "{\"eventId\":\"uuid\",\"productId\":2}")
            );

            // act
            productRankingConsumer.consumeRankingEvents(records, acknowledgment);

            // assert
            then(rankingService).should().processBatch(eventsCaptor.capture());
            assertThat(eventsCaptor.getValue()).hasSize(1);
            then(acknowledgment).should().acknowledge();
        }
    }

    @DisplayName("랭킹 적재가 실패해도,")
    @Nested
    class RankingFailure {

        @DisplayName("ACK은 정상 수행된다.")
        @Test
        void acknowledgesEvenWhenRankingFails() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "product-viewed-v1", 0, 0, "1", "{\"eventId\":\"uuid\",\"productId\":1}");
            doThrow(new RuntimeException("Redis connection refused"))
                    .when(rankingService).processBatch(anyList());

            // act
            productRankingConsumer.consumeRankingEvents(List.of(record), acknowledgment);

            // assert
            then(acknowledgment).should().acknowledge();
        }
    }

    @DisplayName("상품 삭제 이벤트를 소비할 때,")
    @Nested
    class ConsumeProductDeletedEvents {

        @DisplayName("파싱 후 removeProducts에 Delete 이벤트 목록을 전달한다.")
        @Test
        void callsRemoveProducts_withDeleteEvents() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "product-deleted-v1", 0, 0, "1", "{\"eventId\":\"uuid\",\"productId\":5}");

            // act
            productRankingConsumer.consumeProductDeletedEvents(List.of(record), acknowledgment);

            // assert
            then(rankingService).should().removeProducts(
                    List.of(new RankingEvent.Delete("uuid", 5L)));
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("랭킹 제거 실패해도 ACK은 정상 수행된다.")
        @Test
        void acknowledgesEvenWhenRemoveFails() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "product-deleted-v1", 0, 0, "1", "{\"eventId\":\"uuid\",\"productId\":5}");
            doThrow(new RuntimeException("Redis connection refused"))
                    .when(rankingService).removeProducts(anyList());

            // act
            productRankingConsumer.consumeProductDeletedEvents(List.of(record), acknowledgment);

            // assert
            then(acknowledgment).should().acknowledge();
        }
    }
}
