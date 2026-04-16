package com.loopers.application.ranking;

import com.loopers.event.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RankingService 테스트")
class RankingServiceTest {

    private RankingService rankingService;
    private RedisTemplate<String, String> redisTemplate;
    private ZSetOperations<String, String> zSetOperations;
    private RankingScoreCalculator calculator;
    private RankingKeyGenerator keyGenerator;
    private RankingProperties properties;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        properties = new RankingProperties(
            0.1,   // viewWeight
            0.2,   // likeWeight
            0.7,   // orderWeight
            "ranking:all:",  // keyPrefix
            2      // ttlDays
        );
        calculator = new RankingScoreCalculator(properties);
        keyGenerator = new RankingKeyGenerator(properties);
        rankingService = new RankingService(redisTemplate, calculator, keyGenerator);
    }

    @Nested
    @DisplayName("점수 추가")
    class AddScore {

        @Test
        @DisplayName("조회 이벤트 점수를 Redis ZINCRBY로 추가한다")
        void 조회_점수_추가() {
            // Arrange
            Long productId = 1L;
            EventType eventType = EventType.PRODUCT_VIEWED;
            Double eventValue = 1.0;
            String expectedKey = keyGenerator.generateTodayKey();
            double expectedScore = calculator.calculate(eventType, eventValue);

            when(zSetOperations.incrementScore(anyString(), anyString(), anyDouble())).thenReturn(expectedScore);

            // Act
            rankingService.addScore(productId, eventType, eventValue);

            // Assert
            verify(zSetOperations).incrementScore(expectedKey, String.valueOf(productId), expectedScore);
            verify(redisTemplate).expire(eq(expectedKey), eq(2L), eq(TimeUnit.DAYS));
        }

        @Test
        @DisplayName("주문 이벤트 점수를 Redis ZINCRBY로 추가한다")
        void 주문_점수_추가() {
            // Arrange
            Long productId = 1L;
            EventType eventType = EventType.ORDER_COMPLETED;
            Double eventValue = 50000.0; // price * quantity
            String expectedKey = keyGenerator.generateTodayKey();
            double expectedScore = calculator.calculate(eventType, eventValue);

            when(zSetOperations.incrementScore(anyString(), anyString(), anyDouble())).thenReturn(expectedScore);

            // Act
            rankingService.addScore(productId, eventType, eventValue);

            // Assert
            verify(zSetOperations).incrementScore(expectedKey, String.valueOf(productId), expectedScore);
        }

        @Test
        @DisplayName("좋아요 취소 시 음수 점수로 감소한다")
        void 좋아요_취소_점수_감소() {
            // Arrange
            Long productId = 1L;
            EventType eventType = EventType.LIKE_CANCELED;
            Double eventValue = 1.0;
            String expectedKey = keyGenerator.generateTodayKey();
            double expectedScore = calculator.calculate(eventType, eventValue); // -0.2

            when(zSetOperations.incrementScore(anyString(), anyString(), anyDouble())).thenReturn(0.0);

            // Act
            rankingService.addScore(productId, eventType, eventValue);

            // Assert
            verify(zSetOperations).incrementScore(expectedKey, String.valueOf(productId), expectedScore);
            assertThat(expectedScore).isNegative();
        }

        @Test
        @DisplayName("지원하지 않는 이벤트 타입은 Redis 호출하지 않는다")
        void 미지원_이벤트_무시() {
            // Arrange
            Long productId = 1L;
            EventType eventType = EventType.COUPON_ISSUE_REQUESTED;
            Double eventValue = 1.0;

            // Act
            rankingService.addScore(productId, eventType, eventValue);

            // Assert
            verify(zSetOperations, never()).incrementScore(anyString(), anyString(), anyDouble());
        }
    }

    @Nested
    @DisplayName("특정 날짜 점수 추가")
    class AddScoreForDate {

        @Test
        @DisplayName("특정 날짜의 키에 점수를 추가한다")
        void 특정_날짜_점수_추가() {
            // Arrange
            Long productId = 1L;
            EventType eventType = EventType.PRODUCT_VIEWED;
            Double eventValue = 1.0;
            LocalDate date = LocalDate.of(2025, 4, 7);
            String expectedKey = "ranking:all:20250407";
            double expectedScore = calculator.calculate(eventType, eventValue);

            when(zSetOperations.incrementScore(anyString(), anyString(), anyDouble())).thenReturn(expectedScore);

            // Act
            rankingService.addScore(productId, eventType, eventValue, date);

            // Assert
            verify(zSetOperations).incrementScore(expectedKey, String.valueOf(productId), expectedScore);
        }
    }
}
