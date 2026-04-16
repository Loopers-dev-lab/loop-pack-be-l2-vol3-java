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

import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Import(TestRedisConfiguration.class)
@DisplayName("RankingService 통합 테스트")
class RankingServiceIntegrationTest {

    @Autowired
    private RankingService rankingService;

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
    @DisplayName("점수 누적 (ZINCRBY)")
    class ScoreAccumulation {

        @Test
        @DisplayName("같은 상품에 여러 이벤트가 발생하면 점수가 누적된다")
        void 점수_누적() {
            // Arrange
            Long productId = 1L;
            String key = keyGenerator.generateTodayKey();
            String member = String.valueOf(productId);

            // Act - 조회 3번
            rankingService.addScore(productId, EventType.PRODUCT_VIEWED, 1.0);
            rankingService.addScore(productId, EventType.PRODUCT_VIEWED, 1.0);
            rankingService.addScore(productId, EventType.PRODUCT_VIEWED, 1.0);

            // Assert
            Double score = redisTemplate.opsForZSet().score(key, member);
            assertThat(score).isNotNull();
            assertThat(score).isCloseTo(0.3, within(0.0001)); // 0.1 * 3
        }

        @Test
        @DisplayName("좋아요 취소 시 점수가 감소한다")
        void 좋아요_취소_점수_감소() {
            // Arrange
            Long productId = 1L;
            String key = keyGenerator.generateTodayKey();
            String member = String.valueOf(productId);

            // Act - 좋아요 생성 2번, 취소 1번
            rankingService.addScore(productId, EventType.LIKE_CREATED, 1.0);
            rankingService.addScore(productId, EventType.LIKE_CREATED, 1.0);
            rankingService.addScore(productId, EventType.LIKE_CANCELED, 1.0);

            // Assert
            Double score = redisTemplate.opsForZSet().score(key, member);
            assertThat(score).isNotNull();
            assertThat(score).isCloseTo(0.2, within(0.0001)); // 0.2 + 0.2 - 0.2
        }

        @Test
        @DisplayName("주문 이벤트는 weight * 1 점수가 계산된다 (건수 누적)")
        void 주문_점수_계산() {
            // Arrange
            Long productId = 1L;
            String key = keyGenerator.generateTodayKey();
            String member = String.valueOf(productId);
            double orderValue = 10000.0 * 2; // price * quantity (사용되지 않음)

            // Act
            rankingService.addScore(productId, EventType.ORDER_COMPLETED, orderValue);

            // Assert
            Double score = redisTemplate.opsForZSet().score(key, member);
            double expectedScore = 0.7; // weight * 1 (단건 기준)
            assertThat(score).isNotNull();
            assertThat(score).isCloseTo(expectedScore, within(0.0001));
        }
    }

    @Nested
    @DisplayName("TTL 설정")
    class TtlSetting {

        @Test
        @DisplayName("새로운 키 생성 시 TTL이 설정된다")
        void 새_키_TTL_설정() {
            // Arrange
            Long productId = 1L;
            String key = keyGenerator.generateTodayKey();

            // Act
            rankingService.addScore(productId, EventType.PRODUCT_VIEWED, 1.0);

            // Assert
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            assertThat(ttl).isNotNull();
            assertThat(ttl).isGreaterThan(0);
            // TTL이 2일(172800초) 이하인지 확인
            assertThat(ttl).isLessThanOrEqualTo(2 * 24 * 60 * 60L);
        }
    }

    @Nested
    @DisplayName("일별 키 분리")
    class DailyKeySeparation {

        @Test
        @DisplayName("다른 날짜에 점수를 추가하면 별도 키에 저장된다")
        void 날짜별_키_분리() {
            // Arrange
            Long productId = 1L;
            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);

            // Act
            rankingService.addScore(productId, EventType.PRODUCT_VIEWED, 1.0, today);
            rankingService.addScore(productId, EventType.PRODUCT_VIEWED, 1.0, yesterday);
            rankingService.addScore(productId, EventType.PRODUCT_VIEWED, 1.0, yesterday);

            // Assert
            String todayKey = keyGenerator.generateDailyKey(today);
            String yesterdayKey = keyGenerator.generateDailyKey(yesterday);

            Double todayScore = redisTemplate.opsForZSet().score(todayKey, String.valueOf(productId));
            Double yesterdayScore = redisTemplate.opsForZSet().score(yesterdayKey, String.valueOf(productId));

            assertThat(todayScore).isCloseTo(0.1, within(0.0001)); // 1회
            assertThat(yesterdayScore).isCloseTo(0.2, within(0.0001)); // 2회
        }
    }
}
