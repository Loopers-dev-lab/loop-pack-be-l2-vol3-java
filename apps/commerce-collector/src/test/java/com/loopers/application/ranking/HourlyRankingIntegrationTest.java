package com.loopers.application.ranking;

import com.loopers.application.consumer.MetricsEventConsumer;
import com.loopers.config.TestRedisConfiguration;
import com.loopers.event.AggregateType;
import com.loopers.event.EventEnvelope;
import com.loopers.event.EventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 시간별 랭킹 통합 테스트.
 * 이벤트 처리 시 Daily + Hourly 키 모두에 점수가 반영되는지 검증합니다.
 */
@SpringBootTest
@Import(TestRedisConfiguration.class)
@DisplayName("시간별 랭킹 통합 테스트")
class HourlyRankingIntegrationTest {

    private static final DateTimeFormatter HOURLY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");

    @Autowired
    private MetricsEventConsumer consumer;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RankingKeyGenerator keyGenerator;

    @BeforeEach
    void setUp() {
        cleanUpRedisKeys();
    }

    @AfterEach
    void tearDown() {
        cleanUpRedisKeys();
    }

    private void cleanUpRedisKeys() {
        Set<String> keys = redisTemplate.keys("ranking:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String getHourlyKey() {
        return keyGenerator.generateHourlyKey(LocalDateTime.now());
    }

    private EventEnvelope createEnvelope(EventType eventType, String aggregateId, String payload) {
        return new EventEnvelope(
            UUID.randomUUID().toString(),
            eventType.name(),
            AggregateType.PRODUCT.name(),
            aggregateId,
            Instant.now(),
            payload
        );
    }

    @Nested
    @DisplayName("Hourly 키 생성")
    class HourlyKeyGeneration {

        @Test
        @DisplayName("시간별 키가 올바른 형식으로 생성된다")
        void 시간별_키_형식() {
            // Arrange
            LocalDateTime dateTime = LocalDateTime.of(2025, 4, 7, 14, 30);

            // Act
            String hourlyKey = keyGenerator.generateHourlyKey(dateTime);

            // Assert
            assertThat(hourlyKey).isEqualTo("ranking:hourly:2025040714");
        }

        @Test
        @DisplayName("현재 시간 기준 시간별 키가 생성된다")
        void 현재_시간_키() {
            // Act
            String hourlyKey = keyGenerator.generateCurrentHourKey();

            // Assert
            LocalDateTime now = LocalDateTime.now();
            String expected = "ranking:hourly:" + now.format(HOURLY_FORMATTER);
            assertThat(hourlyKey).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("이벤트 처리 시 Hourly 키 반영")
    class EventToHourlyKey {

        @Test
        @DisplayName("조회 이벤트 처리 시 Hourly 키에도 점수가 반영된다")
        void 조회_이벤트_Hourly_반영() {
            // Arrange
            Long productId = 100L;
            EventEnvelope envelope = createEnvelope(
                EventType.PRODUCT_VIEWED,
                String.valueOf(productId),
                "{}"
            );

            // Act
            consumer.processEvent(envelope);

            // Assert
            String hourlyKey = getHourlyKey();
            Double score = redisTemplate.opsForZSet().score(hourlyKey, String.valueOf(productId));
            assertThat(score).isNotNull();
            assertThat(score).isCloseTo(0.1, within(0.0001));
        }

        @Test
        @DisplayName("좋아요 이벤트 처리 시 Hourly 키에도 점수가 반영된다")
        void 좋아요_이벤트_Hourly_반영() {
            // Arrange
            Long productId = 100L;
            EventEnvelope envelope = createEnvelope(
                EventType.LIKE_CREATED,
                String.valueOf(productId),
                "{}"
            );

            // Act
            consumer.processEvent(envelope);

            // Assert
            String hourlyKey = getHourlyKey();
            Double score = redisTemplate.opsForZSet().score(hourlyKey, String.valueOf(productId));
            assertThat(score).isNotNull();
            assertThat(score).isCloseTo(0.2, within(0.0001));
        }

        @Test
        @DisplayName("주문 이벤트 처리 시 Hourly 키에도 점수가 반영된다")
        void 주문_이벤트_Hourly_반영() {
            // Arrange
            Long productId = 100L;
            String payload = "{\"orderId\":1,\"userId\":1,\"productId\":100,\"quantity\":1,\"totalAmount\":10000}";
            EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(),
                EventType.ORDER_COMPLETED.name(),
                AggregateType.ORDER.name(),
                "1",
                Instant.now(),
                payload
            );

            // Act
            consumer.processEvent(envelope);

            // Assert
            String hourlyKey = getHourlyKey();
            Double score = redisTemplate.opsForZSet().score(hourlyKey, String.valueOf(productId));
            assertThat(score).isNotNull();
            assertThat(score).isCloseTo(0.7, within(0.0001));
        }
    }

    @Nested
    @DisplayName("TTL 설정")
    class HourlyTTL {

        @Test
        @DisplayName("Hourly 키에 3시간 TTL이 설정된다")
        void Hourly_TTL_3시간() {
            // Arrange
            Long productId = 100L;
            EventEnvelope envelope = createEnvelope(
                EventType.PRODUCT_VIEWED,
                String.valueOf(productId),
                "{}"
            );

            // Act
            consumer.processEvent(envelope);

            // Assert
            String hourlyKey = getHourlyKey();
            Long ttl = redisTemplate.getExpire(hourlyKey, TimeUnit.SECONDS);

            assertThat(ttl).isNotNull();
            assertThat(ttl).isGreaterThan(0);
            // TTL이 3시간(10800초) 이하인지 확인
            assertThat(ttl).isLessThanOrEqualTo(3 * 60 * 60L);
        }
    }
}