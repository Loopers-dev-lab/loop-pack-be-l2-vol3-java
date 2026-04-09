package com.loopers.interfaces.consumer;

import com.loopers.infrastructure.ranking.RankingRedisRepository;
import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "spring.kafka.listener.auto-startup=false"
})
@Import({KafkaTestContainersConfig.class, RedisTestContainersConfig.class})
class UserActionEventConsumerIntegrationTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private UserActionEventConsumer userActionEventConsumer;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("handleUserActionEvents() — 실제 Redis 연동 시,")
    @Nested
    class HandleUserActionEventsIntegration {

        @DisplayName("PRODUCT_VIEWED 이벤트 처리 후 오늘 날짜 ZSET에 +0.1 점수가 적재된다.")
        @Test
        void incrementsRankingZSetScore_whenProductViewedEventProcessed() {
            // arrange
            Long productId = 100L;
            long nowMs = System.currentTimeMillis();
            UserActionEventConsumer.UserActionEventMessage message =
                new UserActionEventConsumer.UserActionEventMessage("PRODUCT_VIEWED", 1L, productId, null);
            ConsumerRecord<String, UserActionEventConsumer.UserActionEventMessage> record =
                new ConsumerRecord<>("user-action-events", 0, 0L, nowMs, TimestampType.CREATE_TIME,
                    -1, -1, String.valueOf(productId), message, new RecordHeaders(), Optional.empty());
            Acknowledgment acknowledgment = mock(Acknowledgment.class);

            // act
            userActionEventConsumer.handleUserActionEvents(List.of(record), acknowledgment);

            // assert
            String key = RankingRedisRepository.buildKey(LocalDate.now());
            Double score = redisTemplate.opsForZSet().score(key, String.valueOf(productId));
            assertThat(score).isEqualTo(0.1);
        }

        @DisplayName("PRODUCT_VIEWED 이외의 이벤트는 ZSET에 점수를 적재하지 않는다.")
        @Test
        void doesNotUpdateZSet_whenEventTypeIsNotProductViewed() {
            // arrange
            Long productId = 200L;
            long nowMs = System.currentTimeMillis();
            UserActionEventConsumer.UserActionEventMessage message =
                new UserActionEventConsumer.UserActionEventMessage("ORDER_CREATED", 1L, productId, null);
            ConsumerRecord<String, UserActionEventConsumer.UserActionEventMessage> record =
                new ConsumerRecord<>("user-action-events", 0, 0L, nowMs, TimestampType.CREATE_TIME,
                    -1, -1, String.valueOf(productId), message, new RecordHeaders(), Optional.empty());
            Acknowledgment acknowledgment = mock(Acknowledgment.class);

            // act
            userActionEventConsumer.handleUserActionEvents(List.of(record), acknowledgment);

            // assert
            String key = RankingRedisRepository.buildKey(LocalDate.now());
            Double score = redisTemplate.opsForZSet().score(key, String.valueOf(productId));
            assertThat(score).isNull();
        }

        @DisplayName("PRODUCT_VIEWED 이벤트를 여러 번 처리하면 점수가 누적된다.")
        @Test
        void accumulatesScore_whenMultipleProductViewedEventsProcessed() {
            // arrange
            Long productId = 300L;
            long nowMs = System.currentTimeMillis();
            UserActionEventConsumer.UserActionEventMessage message =
                new UserActionEventConsumer.UserActionEventMessage("PRODUCT_VIEWED", 1L, productId, null);
            List<ConsumerRecord<String, UserActionEventConsumer.UserActionEventMessage>> records = List.of(
                new ConsumerRecord<>("user-action-events", 0, 0L, nowMs, TimestampType.CREATE_TIME,
                    -1, -1, String.valueOf(productId), message, new RecordHeaders(), Optional.empty()),
                new ConsumerRecord<>("user-action-events", 0, 1L, nowMs, TimestampType.CREATE_TIME,
                    -1, -1, String.valueOf(productId), message, new RecordHeaders(), Optional.empty()),
                new ConsumerRecord<>("user-action-events", 0, 2L, nowMs, TimestampType.CREATE_TIME,
                    -1, -1, String.valueOf(productId), message, new RecordHeaders(), Optional.empty())
            );
            Acknowledgment acknowledgment = mock(Acknowledgment.class);

            // act
            userActionEventConsumer.handleUserActionEvents(records, acknowledgment);

            // assert: 0.1 * 3 = 0.3
            String key = RankingRedisRepository.buildKey(LocalDate.now());
            Double score = redisTemplate.opsForZSet().score(key, String.valueOf(productId));
            assertThat(score).isCloseTo(0.3, org.assertj.core.data.Offset.offset(0.001));
        }
    }
}
