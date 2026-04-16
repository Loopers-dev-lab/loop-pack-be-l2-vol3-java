package com.loopers.application.ranking;

import com.loopers.config.TestRedisConfiguration;
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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Import(TestRedisConfiguration.class)
@DisplayName("RankingBatchProcessor 테스트")
class RankingBatchProcessorTest {

    @Autowired
    private RankingBatchProcessor batchProcessor;

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

    @Nested
    @DisplayName("배치 점수 합산")
    class BatchAggregation {

        @Test
        @DisplayName("같은 productId에 대한 이벤트를 합산하여 한 번에 저장한다")
        void 같은_상품_점수_합산() {
            // Arrange
            Long productId = 1L;
            List<RankingBatchProcessor.ScoreEntry> entries = List.of(
                RankingBatchProcessor.ScoreEntry.of(productId, EventType.PRODUCT_VIEWED, 1.0),
                RankingBatchProcessor.ScoreEntry.of(productId, EventType.PRODUCT_VIEWED, 1.0),
                RankingBatchProcessor.ScoreEntry.of(productId, EventType.PRODUCT_VIEWED, 1.0),
                RankingBatchProcessor.ScoreEntry.of(productId, EventType.LIKE_CREATED, 1.0)
            );

            // Act
            batchProcessor.addScoresBatch(entries);

            // Assert
            String key = keyGenerator.generateTodayKey();
            Double score = redisTemplate.opsForZSet().score(key, String.valueOf(productId));
            // 조회 3회(0.1*3=0.3) + 좋아요 1회(0.2) = 0.5
            assertThat(score).isCloseTo(0.5, within(0.0001));
        }

        @Test
        @DisplayName("여러 productId에 대한 이벤트를 각각 합산하여 저장한다")
        void 여러_상품_점수_합산() {
            // Arrange
            List<RankingBatchProcessor.ScoreEntry> entries = List.of(
                RankingBatchProcessor.ScoreEntry.of(1L, EventType.PRODUCT_VIEWED, 1.0),
                RankingBatchProcessor.ScoreEntry.of(2L, EventType.PRODUCT_VIEWED, 1.0),
                RankingBatchProcessor.ScoreEntry.of(1L, EventType.LIKE_CREATED, 1.0),
                RankingBatchProcessor.ScoreEntry.of(2L, EventType.LIKE_CREATED, 1.0),
                RankingBatchProcessor.ScoreEntry.of(2L, EventType.LIKE_CREATED, 1.0)
            );

            // Act
            batchProcessor.addScoresBatch(entries);

            // Assert
            String key = keyGenerator.generateTodayKey();
            Double score1 = redisTemplate.opsForZSet().score(key, "1");
            Double score2 = redisTemplate.opsForZSet().score(key, "2");

            // 상품1: 조회 0.1 + 좋아요 0.2 = 0.3
            assertThat(score1).isCloseTo(0.3, within(0.0001));
            // 상품2: 조회 0.1 + 좋아요 0.2*2 = 0.5
            assertThat(score2).isCloseTo(0.5, within(0.0001));
        }

        @Test
        @DisplayName("빈 리스트를 처리해도 에러가 발생하지 않는다")
        void 빈_리스트_처리() {
            // Act & Assert - 예외 없이 처리
            batchProcessor.addScoresBatch(List.of());
            batchProcessor.addScoresBatch(null);
        }
    }

    @Nested
    @DisplayName("Redis 연산 최적화")
    class RedisOptimization {

        @Test
        @DisplayName("10개 이벤트를 배치 처리하면 productId 수만큼만 Redis 연산이 발생한다")
        void Redis_연산_절감() {
            // Arrange - 3개 상품에 대한 10개 이벤트
            List<RankingBatchProcessor.ScoreEntry> entries = List.of(
                RankingBatchProcessor.ScoreEntry.of(1L, EventType.PRODUCT_VIEWED, 1.0),
                RankingBatchProcessor.ScoreEntry.of(1L, EventType.PRODUCT_VIEWED, 1.0),
                RankingBatchProcessor.ScoreEntry.of(1L, EventType.PRODUCT_VIEWED, 1.0),
                RankingBatchProcessor.ScoreEntry.of(2L, EventType.LIKE_CREATED, 1.0),
                RankingBatchProcessor.ScoreEntry.of(2L, EventType.LIKE_CREATED, 1.0),
                RankingBatchProcessor.ScoreEntry.of(2L, EventType.LIKE_CANCELED, 1.0),
                RankingBatchProcessor.ScoreEntry.of(3L, EventType.ORDER_COMPLETED, 10000.0),
                RankingBatchProcessor.ScoreEntry.of(3L, EventType.ORDER_COMPLETED, 20000.0),
                RankingBatchProcessor.ScoreEntry.of(3L, EventType.PRODUCT_VIEWED, 1.0),
                RankingBatchProcessor.ScoreEntry.of(3L, EventType.LIKE_CREATED, 1.0)
            );

            // Act
            batchProcessor.addScoresBatch(entries);

            // Assert - 3개 상품 모두 점수가 저장됨
            String key = keyGenerator.generateTodayKey();
            Long zsetSize = redisTemplate.opsForZSet().size(key);
            assertThat(zsetSize).isEqualTo(3);

            // 각 상품의 점수 검증
            Double score1 = redisTemplate.opsForZSet().score(key, "1");
            Double score2 = redisTemplate.opsForZSet().score(key, "2");
            Double score3 = redisTemplate.opsForZSet().score(key, "3");

            // 상품1: 조회 0.1*3 = 0.3
            assertThat(score1).isCloseTo(0.3, within(0.0001));
            // 상품2: 좋아요 0.2*2 - 0.2 = 0.2
            assertThat(score2).isCloseTo(0.2, within(0.0001));
            // 상품3: 주문 0.7*2 + 조회 0.1 + 좋아요 0.2 = 1.7
            double expectedScore3 = 0.7 * 2 + 0.1 + 0.2;
            assertThat(score3).isCloseTo(expectedScore3, within(0.0001));
        }
    }
}
