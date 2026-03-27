package com.loopers.interfaces.consumer;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueService;
import com.loopers.interfaces.consumer.support.KafkaMessageParser;

@ExtendWith(MockitoExtension.class)
class CouponIssueConsumerTest {

    private CouponIssueConsumer couponIssueConsumer;

    @Mock
    private CouponIssueService couponIssueService;

    @Mock
    private Acknowledgment acknowledgment;

    @BeforeEach
    void setUp() {
        KafkaMessageParser kafkaMessageParser = new KafkaMessageParser(new ObjectMapper());
        couponIssueConsumer = new CouponIssueConsumer(couponIssueService, kafkaMessageParser);
    }

    @DisplayName("쿠폰 발급 이벤트를 소비할 때,")
    @Nested
    class Consume {

        @DisplayName("정상 메시지이면, Service에 위임하고 ACK한다.")
        @Test
        void delegatesToService_whenValidMessage() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "coupon-issue-v1", 0, 0, "1",
                    "{\"couponId\":1,\"userId\":100,\"requestedAt\":\"2026-03-27T10:00:00\"}");

            // act
            couponIssueConsumer.consume(List.of(record), acknowledgment);

            // assert
            then(couponIssueService).should().issue("coupon-issue:1:100", 1L, 100L);
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("처리 중 예외가 발생하면, skip하고 나머지를 계속 처리한다.")
        @Test
        void skipsFailedRecord_andContinues() {
            // arrange
            ConsumerRecord<String, Object> badRecord = new ConsumerRecord<>(
                    "coupon-issue-v1", 0, 0, "1", "invalid-json");
            ConsumerRecord<String, Object> goodRecord = new ConsumerRecord<>(
                    "coupon-issue-v1", 0, 1, "2",
                    "{\"couponId\":2,\"userId\":200,\"requestedAt\":\"2026-03-27T10:00:00\"}");

            // act
            couponIssueConsumer.consume(List.of(badRecord, goodRecord), acknowledgment);

            // assert
            then(couponIssueService).should().issue("coupon-issue:2:200", 2L, 200L);
            then(acknowledgment).should().acknowledge();
        }

        @DisplayName("Service에서 예외가 발생하면, skip하고 ACK한다.")
        @Test
        void skipsAndAcks_whenServiceThrows() {
            // arrange
            ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                    "coupon-issue-v1", 0, 0, "1",
                    "{\"couponId\":1,\"userId\":100,\"requestedAt\":\"2026-03-27T10:00:00\"}");
            willThrow(new IllegalArgumentException("쿠폰을 찾을 수 없습니다"))
                    .given(couponIssueService).issue(anyString(), anyLong(), anyLong());

            // act
            couponIssueConsumer.consume(List.of(record), acknowledgment);

            // assert
            then(acknowledgment).should().acknowledge();
        }
    }
}
