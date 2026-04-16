package com.loopers.application.ranking;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 랭킹 시스템 E2E 통합 테스트.
 * 이벤트 처리 → ZSET 반영 → 점수 검증 흐름을 테스트합니다.
 */
@SpringBootTest
@Import(TestRedisConfiguration.class)
@DisplayName("랭킹 E2E 통합 테스트")
class RankingE2EIntegrationTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    @Autowired
    private MetricsEventConsumer consumer;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RankingKeyGenerator keyGenerator;

    @Autowired
    private ObjectMapper objectMapper;

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

    private String getTodayKey() {
        return "ranking:all:" + LocalDate.now().format(DATE_FORMATTER);
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
    @DisplayName("이벤트 처리 → ZSET 반영 흐름")
    class EventToZSetFlow {

        @Test
        @DisplayName("조회 이벤트 처리 후 ZSET에 점수가 반영된다")
        void 조회_이벤트_ZSET_반영() {
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
            String key = getTodayKey();
            Double score = redisTemplate.opsForZSet().score(key, String.valueOf(productId));
            assertThat(score).isNotNull();
            assertThat(score).isCloseTo(0.1, within(0.0001)); // view weight = 0.1
        }

        @Test
        @DisplayName("좋아요 이벤트 처리 후 ZSET에 점수가 반영된다")
        void 좋아요_이벤트_ZSET_반영() {
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
            String key = getTodayKey();
            Double score = redisTemplate.opsForZSet().score(key, String.valueOf(productId));
            assertThat(score).isNotNull();
            assertThat(score).isCloseTo(0.2, within(0.0001)); // like weight = 0.2
        }

        @Test
        @DisplayName("주문 이벤트 처리 후 ZSET에 점수가 반영된다")
        void 주문_이벤트_ZSET_반영() {
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
            String key = getTodayKey();
            Double score = redisTemplate.opsForZSet().score(key, String.valueOf(productId));
            assertThat(score).isNotNull();
            assertThat(score).isCloseTo(0.7, within(0.0001)); // order weight = 0.7
        }

        @Test
        @DisplayName("배치 이벤트 처리 후 점수가 합산되어 반영된다")
        void 배치_이벤트_점수_합산() {
            // Arrange
            Long productId = 100L;
            List<EventEnvelope> envelopes = List.of(
                createEnvelope(EventType.PRODUCT_VIEWED, String.valueOf(productId), "{}"),
                createEnvelope(EventType.PRODUCT_VIEWED, String.valueOf(productId), "{}"),
                createEnvelope(EventType.LIKE_CREATED, String.valueOf(productId), "{}")
            );

            // Act
            consumer.processEventsBatch(envelopes);

            // Assert
            String key = getTodayKey();
            Double score = redisTemplate.opsForZSet().score(key, String.valueOf(productId));
            // 조회 2건(0.1*2=0.2) + 좋아요 1건(0.2) = 0.4
            assertThat(score).isNotNull();
            assertThat(score).isCloseTo(0.4, within(0.0001));
        }
    }

    @Nested
    @DisplayName("가중치 검증")
    class WeightVerification {

        @Test
        @DisplayName("주문 1건(0.7) > 좋아요 3건(0.6)")
        void 주문이_좋아요보다_높음() {
            // Arrange
            Long productA = 100L;
            Long productB = 200L;

            // 상품A: 주문 1건
            String orderPayload = "{\"orderId\":1,\"userId\":1,\"productId\":100,\"quantity\":1,\"totalAmount\":10000}";
            EventEnvelope orderEvent = new EventEnvelope(
                UUID.randomUUID().toString(),
                EventType.ORDER_COMPLETED.name(),
                AggregateType.ORDER.name(),
                "1",
                Instant.now(),
                orderPayload
            );

            // 상품B: 좋아요 3건
            List<EventEnvelope> likeEvents = List.of(
                createEnvelope(EventType.LIKE_CREATED, String.valueOf(productB), "{}"),
                createEnvelope(EventType.LIKE_CREATED, String.valueOf(productB), "{}"),
                createEnvelope(EventType.LIKE_CREATED, String.valueOf(productB), "{}")
            );

            // Act
            consumer.processEvent(orderEvent);
            consumer.processEventsBatch(likeEvents);

            // Assert
            String key = getTodayKey();
            Double scoreA = redisTemplate.opsForZSet().score(key, String.valueOf(productA));
            Double scoreB = redisTemplate.opsForZSet().score(key, String.valueOf(productB));

            assertThat(scoreA).isCloseTo(0.7, within(0.0001));
            assertThat(scoreB).isCloseTo(0.6, within(0.0001));
            assertThat(scoreA).isGreaterThan(scoreB);

            // ZREVRANK 확인 (0-based, 낮을수록 높은 순위)
            Long rankA = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productA));
            Long rankB = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productB));
            assertThat(rankA).isEqualTo(0); // 1위
            assertThat(rankB).isEqualTo(1); // 2위
        }

        @Test
        @DisplayName("조회 10건(1.0) + 좋아요 2건(0.4) > 주문 1건(0.7)")
        void 복합_이벤트가_주문보다_높음() {
            // Arrange
            Long productA = 100L;
            Long productB = 200L;

            // 상품A: 조회 10건 + 좋아요 2건
            List<EventEnvelope> viewEvents = new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) {
                viewEvents.add(createEnvelope(EventType.PRODUCT_VIEWED, String.valueOf(productA), "{}"));
            }
            viewEvents.add(createEnvelope(EventType.LIKE_CREATED, String.valueOf(productA), "{}"));
            viewEvents.add(createEnvelope(EventType.LIKE_CREATED, String.valueOf(productA), "{}"));

            // 상품B: 주문 1건
            String orderPayload = "{\"orderId\":1,\"userId\":1,\"productId\":200,\"quantity\":1,\"totalAmount\":10000}";
            EventEnvelope orderEvent = new EventEnvelope(
                UUID.randomUUID().toString(),
                EventType.ORDER_COMPLETED.name(),
                AggregateType.ORDER.name(),
                "1",
                Instant.now(),
                orderPayload
            );

            // Act
            consumer.processEventsBatch(viewEvents);
            consumer.processEvent(orderEvent);

            // Assert
            String key = getTodayKey();
            Double scoreA = redisTemplate.opsForZSet().score(key, String.valueOf(productA));
            Double scoreB = redisTemplate.opsForZSet().score(key, String.valueOf(productB));

            // 상품A: 0.1*10 + 0.2*2 = 1.4
            assertThat(scoreA).isCloseTo(1.4, within(0.0001));
            // 상품B: 0.7
            assertThat(scoreB).isCloseTo(0.7, within(0.0001));
            assertThat(scoreA).isGreaterThan(scoreB);
        }
    }

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("좋아요 취소 시 점수가 감소한다")
        void 좋아요_취소_점수_감소() {
            // Arrange
            Long productId = 100L;
            List<EventEnvelope> envelopes = List.of(
                createEnvelope(EventType.LIKE_CREATED, String.valueOf(productId), "{}"),
                createEnvelope(EventType.LIKE_CREATED, String.valueOf(productId), "{}"),
                createEnvelope(EventType.LIKE_CREATED, String.valueOf(productId), "{}"),
                createEnvelope(EventType.LIKE_CANCELED, String.valueOf(productId), "{}")
            );

            // Act
            consumer.processEventsBatch(envelopes);

            // Assert
            String key = getTodayKey();
            Double score = redisTemplate.opsForZSet().score(key, String.valueOf(productId));
            // 좋아요 3건(0.6) - 취소 1건(0.2) = 0.4
            assertThat(score).isCloseTo(0.4, within(0.0001));
        }

        @Test
        @DisplayName("중복 이벤트는 멱등성 체크로 한 번만 처리된다")
        void 중복_이벤트_멱등성() {
            // Arrange
            Long productId = 100L;
            String eventId = UUID.randomUUID().toString();
            EventEnvelope duplicateEvent = new EventEnvelope(
                eventId,
                EventType.PRODUCT_VIEWED.name(),
                AggregateType.PRODUCT.name(),
                String.valueOf(productId),
                Instant.now(),
                "{}"
            );

            // Act - 같은 이벤트를 3번 처리
            consumer.processEvent(duplicateEvent);
            consumer.processEvent(duplicateEvent);
            consumer.processEvent(duplicateEvent);

            // Assert - 1번만 반영됨
            String key = getTodayKey();
            Double score = redisTemplate.opsForZSet().score(key, String.valueOf(productId));
            assertThat(score).isCloseTo(0.1, within(0.0001));
        }

        @Test
        @DisplayName("점수가 음수가 되어도 ZSET에 유지된다")
        void 음수_점수_유지() {
            // Arrange - 좋아요 1건 후 2건 취소
            Long productId = 100L;
            List<EventEnvelope> envelopes = List.of(
                createEnvelope(EventType.LIKE_CREATED, String.valueOf(productId), "{}"),
                createEnvelope(EventType.LIKE_CANCELED, String.valueOf(productId), "{}"),
                createEnvelope(EventType.LIKE_CANCELED, String.valueOf(productId), "{}")
            );

            // Act
            consumer.processEventsBatch(envelopes);

            // Assert
            String key = getTodayKey();
            Double score = redisTemplate.opsForZSet().score(key, String.valueOf(productId));
            // 0.2 - 0.2 - 0.2 = -0.2
            assertThat(score).isCloseTo(-0.2, within(0.0001));
        }

        @Test
        @DisplayName("여러 상품의 랭킹이 올바르게 정렬된다")
        void 여러_상품_랭킹_정렬() {
            // Arrange
            Long product1 = 100L;
            Long product2 = 200L;
            Long product3 = 300L;

            // product1: 주문 1건 (0.7)
            String orderPayload1 = "{\"orderId\":1,\"userId\":1,\"productId\":100,\"quantity\":1,\"totalAmount\":10000}";
            consumer.processEvent(new EventEnvelope(
                UUID.randomUUID().toString(),
                EventType.ORDER_COMPLETED.name(),
                AggregateType.ORDER.name(),
                "1",
                Instant.now(),
                orderPayload1
            ));

            // product2: 조회 5건 (0.5)
            for (int i = 0; i < 5; i++) {
                consumer.processEvent(createEnvelope(EventType.PRODUCT_VIEWED, String.valueOf(product2), "{}"));
            }

            // product3: 좋아요 1건 (0.2)
            consumer.processEvent(createEnvelope(EventType.LIKE_CREATED, String.valueOf(product3), "{}"));

            // Assert - ZREVRANGE로 순위 확인
            String key = getTodayKey();
            Set<String> top3 = redisTemplate.opsForZSet().reverseRange(key, 0, 2);

            assertThat(top3).containsExactly(
                String.valueOf(product1),  // 0.7 - 1위
                String.valueOf(product2),  // 0.5 - 2위
                String.valueOf(product3)   // 0.2 - 3위
            );
        }
    }
}