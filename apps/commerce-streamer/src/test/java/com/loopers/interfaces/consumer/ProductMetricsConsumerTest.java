package com.loopers.interfaces.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.MetricsEventMeta;
import com.loopers.domain.metrics.MetricsPayload;
import com.loopers.application.metrics.ProductMetricsService;
import com.loopers.interfaces.consumer.support.KafkaMessageParser;

@ExtendWith(MockitoExtension.class)
class ProductMetricsConsumerTest {

    @InjectMocks
    private ProductMetricsConsumer productMetricsConsumer;

    @Mock
    private ProductMetricsService productMetricsService;

    @Spy
    private final KafkaMessageParser kafkaMessageParser = new KafkaMessageParser(new ObjectMapper());

    @Mock
    private Acknowledgment acknowledgment;

    @DisplayName("좋아요 이벤트를 소비할 때,")
    @Nested
    class ConsumeLikeEvents {

        @DisplayName("like-liked-v1 토픽이면, LIKED 타입으로 Service에 위임한다.")
        @Test
        void delegatesAsLiked_whenLikedTopic() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "like-liked-v1", 0, 0, "1", "{\"eventId\":\"uuid\",\"productId\":1}");

            // act
            productMetricsConsumer.consumeLikeEvents(List.of(record), acknowledgment);

            // assert
            then(productMetricsService).should().handleEvent(
                    any(MetricsEventMeta.class), any(MetricsPayload.Like.class));
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("like-unliked-v1 토픽이면, UNLIKED 타입으로 Service에 위임한다.")
        @Test
        void delegatesAsUnliked_whenUnlikedTopic() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "like-unliked-v1", 0, 0, "1", "{\"eventId\":\"uuid\",\"productId\":1}");

            // act
            productMetricsConsumer.consumeLikeEvents(List.of(record), acknowledgment);

            // assert
            then(productMetricsService).should().handleEvent(
                    any(MetricsEventMeta.class), any(MetricsPayload.Like.class));
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("처리 중 예외가 발생하면, skip하고 나머지를 계속 처리한다.")
        @Test
        void skipsFailedRecord_andContinues() {
            // arrange
            ConsumerRecord<String, Object> badRecord = new ConsumerRecord<>(
                    "like-liked-v1", 0, 0, "1", "invalid-json");
            ConsumerRecord<String, Object> goodRecord = new ConsumerRecord<>(
                    "like-liked-v1", 0, 1, "2", "{\"eventId\":\"uuid\",\"productId\":2}");

            // act
            productMetricsConsumer.consumeLikeEvents(List.of(badRecord, goodRecord), acknowledgment);

            // assert
            then(productMetricsService).should().handleEvent(
                    any(MetricsEventMeta.class), any(MetricsPayload.Like.class));
            then(acknowledgment).should().acknowledge();
        }
    }

    @DisplayName("주문 완료 이벤트를 소비할 때,")
    @Nested
    class ConsumeOrderEvents {

        @DisplayName("orderItems를 파싱하여 ORDER_COMPLETED 타입으로 Service에 위임한다.")
        @Test
        void delegatesWithOrderItems() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "order-completed-v1", 0, 0, "1",
                    "{\"eventId\":\"uuid\",\"orderId\":1,\"orderItems\":[{\"productId\":10,\"quantity\":2,\"price\":50000},{\"productId\":20,\"quantity\":3,\"price\":30000}]}");

            // act
            productMetricsConsumer.consumeOrderEvents(List.of(record), acknowledgment);

            // assert
            then(productMetricsService).should().handleEvent(
                    any(MetricsEventMeta.class), any(MetricsPayload.Order.class));
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("처리 중 예외가 발생하면, skip하고 ACK한다.")
        @Test
        void skipsFailedRecord() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "order-completed-v1", 0, 0, "1", "{\"eventId\":\"uuid\",\"orderId\":1}");

            // act
            productMetricsConsumer.consumeOrderEvents(List.of(record), acknowledgment);

            // assert
            then(productMetricsService).shouldHaveNoInteractions();
            then(acknowledgment).should().acknowledge();
        }
    }

    @DisplayName("상품 조회 이벤트를 소비할 때,")
    @Nested
    class ConsumeViewEvents {

        @DisplayName("PRODUCT_VIEWED 타입으로 Service에 위임한다.")
        @Test
        void delegatesAsProductViewed() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "product-viewed-v1", 0, 0, "1", "{\"eventId\":\"uuid\",\"productId\":1}");

            // act
            productMetricsConsumer.consumeViewEvents(List.of(record), acknowledgment);

            // assert
            then(productMetricsService).should().handleEvent(
                    any(MetricsEventMeta.class), any(MetricsPayload.View.class));
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("처리 중 예외가 발생하면, skip하고 ACK한다.")
        @Test
        void skipsFailedRecord() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "product-viewed-v1", 0, 0, "1", "invalid-json");

            // act
            productMetricsConsumer.consumeViewEvents(List.of(record), acknowledgment);

            // assert
            then(productMetricsService).shouldHaveNoInteractions();
            then(acknowledgment).should().acknowledge();
        }
    }
}
