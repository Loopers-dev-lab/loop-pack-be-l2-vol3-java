package com.loopers.interfaces.consumer;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ProductMetricsService;
import com.loopers.domain.eventhandled.EventHandledRepository;

@ExtendWith(MockitoExtension.class)
class ProductMetricsConsumerTest {

    @InjectMocks
    private ProductMetricsConsumer productMetricsConsumer;

    @Mock
    private ProductMetricsService productMetricsService;

    @Mock
    private EventHandledRepository eventHandledRepository;

    @Mock
    private Acknowledgment acknowledgment;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @DisplayName("좋아요 이벤트를 소비할 때,")
    @Nested
    class ConsumeLikeEvents {

        @DisplayName("like-liked-v1 토픽이면, incrementLikeCount를 호출한다.")
        @Test
        void incrementsLikeCount_whenLikedTopic() throws Exception {
            // arrange
            given(eventHandledRepository.markIfAbsent("uuid")).willReturn(true);
            JsonNode value = objectMapper.readTree("{\"eventId\":\"uuid\",\"productId\":1}");
            ConsumerRecord<String, JsonNode> record = new ConsumerRecord<>("like-liked-v1", 0, 0, "1", value);

            // act
            productMetricsConsumer.consumeLikeEvents(List.of(record), acknowledgment);

            // assert
            then(productMetricsService).should().incrementLikeCount(1L);
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("like-unliked-v1 토픽이면, decrementLikeCount를 호출한다.")
        @Test
        void decrementsLikeCount_whenUnlikedTopic() throws Exception {
            // arrange
            given(eventHandledRepository.markIfAbsent("uuid")).willReturn(true);
            JsonNode value = objectMapper.readTree("{\"eventId\":\"uuid\",\"productId\":1}");
            ConsumerRecord<String, JsonNode> record = new ConsumerRecord<>("like-unliked-v1", 0, 0, "1", value);

            // act
            productMetricsConsumer.consumeLikeEvents(List.of(record), acknowledgment);

            // assert
            then(productMetricsService).should().decrementLikeCount(1L);
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("중복 이벤트이면, service를 호출하지 않는다.")
        @Test
        void skipsService_whenDuplicateEvent() throws Exception {
            // arrange
            given(eventHandledRepository.markIfAbsent("dup-id")).willReturn(false);
            JsonNode value = objectMapper.readTree("{\"eventId\":\"dup-id\",\"productId\":1}");
            ConsumerRecord<String, JsonNode> record = new ConsumerRecord<>("like-liked-v1", 0, 0, "1", value);

            // act
            productMetricsConsumer.consumeLikeEvents(List.of(record), acknowledgment);

            // assert
            then(productMetricsService).shouldHaveNoInteractions();
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("처리 중 예외가 발생하면, skip하고 나머지를 계속 처리한다.")
        @Test
        void skipsFailedRecord_andContinues() throws Exception {
            // arrange
            given(eventHandledRepository.markIfAbsent("uuid")).willReturn(true);
            JsonNode badValue = objectMapper.readTree("{}");
            JsonNode goodValue = objectMapper.readTree("{\"eventId\":\"uuid\",\"productId\":2}");
            ConsumerRecord<String, JsonNode> badRecord = new ConsumerRecord<>("like-liked-v1", 0, 0, "1", badValue);
            ConsumerRecord<String, JsonNode> goodRecord = new ConsumerRecord<>("like-liked-v1", 0, 1, "2", goodValue);

            // act
            productMetricsConsumer.consumeLikeEvents(List.of(badRecord, goodRecord), acknowledgment);

            // assert
            then(productMetricsService).should().incrementLikeCount(2L);
            then(acknowledgment).should().acknowledge();
        }
    }

    @DisplayName("주문 완료 이벤트를 소비할 때,")
    @Nested
    class ConsumeOrderEvents {

        @DisplayName("orderItems의 각 상품에 대해 addOrderCount를 호출한다.")
        @Test
        void addsOrderCountPerProduct() throws Exception {
            // arrange
            given(eventHandledRepository.markIfAbsent("uuid")).willReturn(true);
            JsonNode value = objectMapper.readTree(
                    "{\"eventId\":\"uuid\",\"orderId\":1,\"orderItems\":[{\"productId\":10,\"quantity\":2},{\"productId\":20,\"quantity\":3}]}");
            ConsumerRecord<String, JsonNode> record = new ConsumerRecord<>("order-completed-v1", 0, 0, "1", value);

            // act
            productMetricsConsumer.consumeOrderEvents(List.of(record), acknowledgment);

            // assert
            then(productMetricsService).should().addOrderCount(10L, 2L);
            then(productMetricsService).should().addOrderCount(20L, 3L);
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("중복 이벤트이면, service를 호출하지 않는다.")
        @Test
        void skipsService_whenDuplicateEvent() throws Exception {
            // arrange
            given(eventHandledRepository.markIfAbsent("dup-id")).willReturn(false);
            JsonNode value = objectMapper.readTree(
                    "{\"eventId\":\"dup-id\",\"orderId\":1,\"orderItems\":[{\"productId\":10,\"quantity\":2}]}");
            ConsumerRecord<String, JsonNode> record = new ConsumerRecord<>("order-completed-v1", 0, 0, "1", value);

            // act
            productMetricsConsumer.consumeOrderEvents(List.of(record), acknowledgment);

            // assert
            then(productMetricsService).shouldHaveNoInteractions();
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("처리 중 예외가 발생하면, skip하고 ACK한다.")
        @Test
        void skipsFailedRecord() throws Exception {
            // arrange
            given(eventHandledRepository.markIfAbsent("uuid")).willReturn(true);
            JsonNode badValue = objectMapper.readTree("{\"eventId\":\"uuid\",\"orderId\":1}");
            ConsumerRecord<String, JsonNode> record = new ConsumerRecord<>("order-completed-v1", 0, 0, "1", badValue);

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

        @DisplayName("incrementViewCount를 호출한다.")
        @Test
        void incrementsViewCount() throws Exception {
            // arrange
            given(eventHandledRepository.markIfAbsent("uuid")).willReturn(true);
            JsonNode value = objectMapper.readTree("{\"eventId\":\"uuid\",\"productId\":1}");
            ConsumerRecord<String, JsonNode> record = new ConsumerRecord<>("product-viewed-v1", 0, 0, "1", value);

            // act
            productMetricsConsumer.consumeViewEvents(List.of(record), acknowledgment);

            // assert
            then(productMetricsService).should().incrementViewCount(1L);
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("중복 이벤트이면, service를 호출하지 않는다.")
        @Test
        void skipsService_whenDuplicateEvent() throws Exception {
            // arrange
            given(eventHandledRepository.markIfAbsent("dup-id")).willReturn(false);
            JsonNode value = objectMapper.readTree("{\"eventId\":\"dup-id\",\"productId\":1}");
            ConsumerRecord<String, JsonNode> record = new ConsumerRecord<>("product-viewed-v1", 0, 0, "1", value);

            // act
            productMetricsConsumer.consumeViewEvents(List.of(record), acknowledgment);

            // assert
            then(productMetricsService).shouldHaveNoInteractions();
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("처리 중 예외가 발생하면, skip하고 ACK한다.")
        @Test
        void skipsFailedRecord() throws Exception {
            // arrange
            JsonNode badValue = objectMapper.readTree("{}");
            ConsumerRecord<String, JsonNode> record = new ConsumerRecord<>("product-viewed-v1", 0, 0, "1", badValue);

            // act
            productMetricsConsumer.consumeViewEvents(List.of(record), acknowledgment);

            // assert
            then(productMetricsService).shouldHaveNoInteractions();
            then(acknowledgment).should().acknowledge();
        }
    }
}
