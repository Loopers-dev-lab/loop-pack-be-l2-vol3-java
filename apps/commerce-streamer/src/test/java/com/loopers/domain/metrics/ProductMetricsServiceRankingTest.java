package com.loopers.domain.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.support.kafka.KafkaOutboxMessage;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductMetricsServiceRankingTest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private ProductMetricsService productMetricsService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private String rankingKey(LocalDate date) {
        return "ranking:all:" + date.format(DATE_FORMAT);
    }

    private KafkaOutboxMessage message(String eventType, Object payload) throws JsonProcessingException {
        return new KafkaOutboxMessage(
            UUID.randomUUID().toString(),
            eventType,
            objectMapper.writeValueAsString(payload),
            null
        );
    }

    @DisplayName("PRODUCT_VIEWED 이벤트 처리 시, ")
    @Nested
    class ViewEvent {

        @DisplayName("오늘 날짜 ZSET에 0.1점이 누적된다.")
        @Test
        void incrementsViewScore() throws JsonProcessingException {
            // arrange
            LocalDate today = LocalDate.now();
            Long productId = 1L;
            KafkaOutboxMessage msg = message("PRODUCT_VIEWED",
                new ProductMetricsService.ViewPayload(productId, "user1", "Mozilla"));

            // act
            productMetricsService.handle(msg);

            // assert
            Double score = redisTemplate.opsForZSet().score(rankingKey(today), productId.toString());
            assertThat(score).isNotNull().isEqualTo(0.1);
        }

        @DisplayName("중복 이벤트는 ZSET 점수에 반영되지 않는다.")
        @Test
        void doesNotIncrementOnDuplicate() throws JsonProcessingException {
            // arrange
            LocalDate today = LocalDate.now();
            Long productId = 1L;
            KafkaOutboxMessage msg = message("PRODUCT_VIEWED",
                new ProductMetricsService.ViewPayload(productId, "user1", "Mozilla"));

            // act
            productMetricsService.handle(msg);
            productMetricsService.handle(msg); // 동일 eventId

            // assert
            Double score = redisTemplate.opsForZSet().score(rankingKey(today), productId.toString());
            assertThat(score).isNotNull().isEqualTo(0.1);
        }
    }

    @DisplayName("LIKE_CREATED 이벤트 처리 시, ")
    @Nested
    class LikeCreatedEvent {

        @DisplayName("오늘 날짜 ZSET에 0.2점이 누적된다.")
        @Test
        void incrementsLikeScore() throws JsonProcessingException {
            // arrange
            LocalDate today = LocalDate.now();
            Long productId = 1L;
            KafkaOutboxMessage msg = message("LIKE_CREATED",
                new ProductMetricsService.LikePayload(99L, productId));

            // act
            productMetricsService.handle(msg);

            // assert
            Double score = redisTemplate.opsForZSet().score(rankingKey(today), productId.toString());
            assertThat(score).isNotNull().isEqualTo(0.2);
        }
    }

    @DisplayName("LIKE_DELETED 이벤트 처리 시, ")
    @Nested
    class LikeDeletedEvent {

        @DisplayName("오늘 날짜 ZSET에 0.2점이 차감된다.")
        @Test
        void decrementsLikeScore() throws JsonProcessingException {
            // arrange
            LocalDate today = LocalDate.now();
            Long productId = 1L;
            // 먼저 좋아요 2개 추가
            productMetricsService.handle(message("LIKE_CREATED",
                new ProductMetricsService.LikePayload(1L, productId)));
            productMetricsService.handle(message("LIKE_CREATED",
                new ProductMetricsService.LikePayload(2L, productId)));

            KafkaOutboxMessage deleteMsg = message("LIKE_DELETED",
                new ProductMetricsService.LikePayload(1L, productId));

            // act
            productMetricsService.handle(deleteMsg);

            // assert
            Double score = redisTemplate.opsForZSet().score(rankingKey(today), productId.toString());
            assertThat(score).isNotNull().isEqualTo(0.2); // 0.4 - 0.2 = 0.2
        }
    }

    @DisplayName("PRODUCT_SOLD 이벤트 처리 시, ")
    @Nested
    class SoldEvent {

        @DisplayName("0.6 * log1p(amount) 점수가 ZSET에 누적된다.")
        @Test
        void incrementsSoldScore() throws JsonProcessingException {
            // arrange
            LocalDate today = LocalDate.now();
            Long productId = 1L;
            long amount = 10000L;
            KafkaOutboxMessage msg = message("PRODUCT_SOLD",
                new ProductMetricsService.ProductSoldPayload(productId, 999L, amount));

            // act
            productMetricsService.handle(msg);

            // assert
            double expected = 0.6 * Math.log1p(amount);
            Double score = redisTemplate.opsForZSet().score(rankingKey(today), productId.toString());
            assertThat(score).isNotNull().isCloseTo(expected, org.assertj.core.data.Offset.offset(0.0001));
        }

        @DisplayName("주문 1건이 좋아요 3건보다 높은 점수를 가진다.")
        @Test
        void soldScoreExceedsThreeLikes() throws JsonProcessingException {
            // arrange
            Long productA = 1L; // 좋아요 3건
            Long productB = 2L; // 주문 1건 (10,000원)

            for (int i = 0; i < 3; i++) {
                productMetricsService.handle(message("LIKE_CREATED",
                    new ProductMetricsService.LikePayload((long) i, productA)));
            }
            productMetricsService.handle(message("PRODUCT_SOLD",
                new ProductMetricsService.ProductSoldPayload(productB, 999L, 10000L)));

            // assert
            LocalDate today = LocalDate.now();
            String key = rankingKey(today);
            Double scoreA = redisTemplate.opsForZSet().score(key, productA.toString()); // 0.6
            Double scoreB = redisTemplate.opsForZSet().score(key, productB.toString()); // 0.6 * log1p(10000) ≈ 5.53

            assertThat(scoreB).isGreaterThan(scoreA);
        }
    }

    @DisplayName("ZSET TTL은 2일로 설정된다.")
    @Test
    void zsetHasTwoDayTtl() throws JsonProcessingException {
        // arrange
        Long productId = 1L;
        KafkaOutboxMessage msg = message("PRODUCT_VIEWED",
            new ProductMetricsService.ViewPayload(productId, "user1", "Mozilla"));

        // act
        productMetricsService.handle(msg);

        // assert
        Long ttl = redisTemplate.getExpire(rankingKey(LocalDate.now()));
        assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(2 * 24 * 60 * 60L);
    }
}
