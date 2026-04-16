package com.loopers.application.ranking;

import com.loopers.config.TestRedisConfiguration;
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
@DisplayName("RankingCarryOverScheduler 테스트")
class RankingCarryOverSchedulerTest {

    @Autowired
    private RankingCarryOverScheduler scheduler;

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
    @DisplayName("점수 이월")
    class CarryOver {

        @Test
        @DisplayName("오늘 점수의 10%가 내일 키에 복사된다")
        void 점수_10퍼센트_복사() {
            // Arrange
            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);
            String todayKey = keyGenerator.generateDailyKey(today);
            String tomorrowKey = keyGenerator.generateDailyKey(tomorrow);

            // 오늘 점수 설정
            redisTemplate.opsForZSet().add(todayKey, "100", 10.0);
            redisTemplate.opsForZSet().add(todayKey, "200", 20.0);
            redisTemplate.opsForZSet().add(todayKey, "300", 5.0);

            // Act
            scheduler.carryOverScores();

            // Assert - 10% 복사 확인
            Double score100 = redisTemplate.opsForZSet().score(tomorrowKey, "100");
            Double score200 = redisTemplate.opsForZSet().score(tomorrowKey, "200");
            Double score300 = redisTemplate.opsForZSet().score(tomorrowKey, "300");

            assertThat(score100).isCloseTo(1.0, within(0.0001));  // 10.0 * 0.1
            assertThat(score200).isCloseTo(2.0, within(0.0001));  // 20.0 * 0.1
            assertThat(score300).isCloseTo(0.5, within(0.0001));  // 5.0 * 0.1
        }

        @Test
        @DisplayName("내일 키에 이미 데이터가 있으면 합산된다")
        void 기존_데이터와_합산() {
            // Arrange
            LocalDate today = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);
            String todayKey = keyGenerator.generateDailyKey(today);
            String tomorrowKey = keyGenerator.generateDailyKey(tomorrow);

            // 오늘 점수
            redisTemplate.opsForZSet().add(todayKey, "100", 10.0);

            // 내일 키에 이미 존재하는 데이터 (자정 넘어서 발생한 이벤트)
            redisTemplate.opsForZSet().add(tomorrowKey, "100", 0.5);

            // Act
            scheduler.carryOverScores();

            // Assert - 기존 0.5 + 이월 1.0 = 1.5
            Double score = redisTemplate.opsForZSet().score(tomorrowKey, "100");
            assertThat(score).isCloseTo(1.5, within(0.0001));
        }

        @Test
        @DisplayName("내일 키에 TTL이 설정된다")
        void TTL_설정() {
            // Arrange
            LocalDate today = LocalDate.now();
            String todayKey = keyGenerator.generateDailyKey(today);
            redisTemplate.opsForZSet().add(todayKey, "100", 10.0);

            // Act
            scheduler.carryOverScores();

            // Assert
            LocalDate tomorrow = today.plusDays(1);
            String tomorrowKey = keyGenerator.generateDailyKey(tomorrow);
            Long ttl = redisTemplate.getExpire(tomorrowKey, TimeUnit.SECONDS);

            assertThat(ttl).isNotNull();
            assertThat(ttl).isGreaterThan(0);
            // TTL이 2일(172800초) 이하인지 확인
            assertThat(ttl).isLessThanOrEqualTo(2 * 24 * 60 * 60L);
        }

        @Test
        @DisplayName("오늘 데이터가 없으면 아무 작업도 하지 않는다")
        void 데이터_없으면_스킵() {
            // Act
            scheduler.carryOverScores();

            // Assert - 내일 키가 생성되지 않음
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            String tomorrowKey = keyGenerator.generateDailyKey(tomorrow);
            Boolean exists = redisTemplate.hasKey(tomorrowKey);

            assertThat(exists).isFalse();
        }
    }
}