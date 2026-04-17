package com.loopers.application.ranking;

import com.loopers.config.redis.RankingKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class ScoreCarryOverSchedulerIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    @Autowired
    private ScoreCarryOverScheduler scoreCarryOverScheduler;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> masterRedisTemplate;

    @AfterEach
    void tearDown() {
        masterRedisTemplate.delete(RankingKeys.dailyKey(TODAY));
        masterRedisTemplate.delete(RankingKeys.dailyKey(TOMORROW));
    }

    @DisplayName("score 이월")
    @Nested
    class CarryOver {

        @Test
        @DisplayName("오늘 ZSET의 점수가 10%로 이월되어 내일 ZSET에 적재된다")
        void carriesOver10Percent_fromTodayToTomorrow() {
            // arrange
            masterRedisTemplate.opsForZSet().add(RankingKeys.dailyKey(TODAY), "100", 500.0);
            masterRedisTemplate.opsForZSet().add(RankingKeys.dailyKey(TODAY), "200", 300.0);

            // act
            scoreCarryOverScheduler.carryOver();

            // assert
            Double scoreA = masterRedisTemplate.opsForZSet().score(RankingKeys.dailyKey(TOMORROW), "100");
            Double scoreB = masterRedisTemplate.opsForZSet().score(RankingKeys.dailyKey(TOMORROW), "200");
            assertThat(scoreA).isNotNull().isCloseTo(50.0, within(0.001));
            assertThat(scoreB).isNotNull().isCloseTo(30.0, within(0.001));
        }

        @Test
        @DisplayName("오늘 ZSET이 비어있으면 내일 ZSET은 생성되지 않는다")
        void doesNotCreateTomorrowZSet_whenTodayIsEmpty() {
            // act
            scoreCarryOverScheduler.carryOver();

            // assert
            Long size = masterRedisTemplate.opsForZSet().size(RankingKeys.dailyKey(TOMORROW));
            assertThat(size == null || size == 0L).isTrue();
        }

        @Test
        @DisplayName("내일 ZSET에 기존 점수가 있으면 이월 점수가 누적된다")
        void accumulatesCarryOver_withExistingTomorrowScores() {
            // arrange — 내일에 미리 200점이 있는 상황 (이미 내일 이벤트가 일부 집계된 경우)
            masterRedisTemplate.opsForZSet().add(RankingKeys.dailyKey(TODAY), "100", 500.0);
            masterRedisTemplate.opsForZSet().add(RankingKeys.dailyKey(TOMORROW), "100", 200.0);

            // act
            scoreCarryOverScheduler.carryOver();

            // assert — 200 + (500 × 0.1) = 250
            Double score = masterRedisTemplate.opsForZSet().score(RankingKeys.dailyKey(TOMORROW), "100");
            assertThat(score).isNotNull().isCloseTo(250.0, within(0.001));
        }

        @Test
        @DisplayName("오늘에만 있는 상품과 오늘/내일 모두 있는 상품이 올바르게 병합된다")
        void mergesCorrectly_whenProductExistsOnlyInTodayOrBoth() {
            // arrange
            // product 100: 오늘에만 존재
            masterRedisTemplate.opsForZSet().add(RankingKeys.dailyKey(TODAY), "100", 400.0);
            // product 200: 오늘(200점) + 내일(100점) 모두 존재
            masterRedisTemplate.opsForZSet().add(RankingKeys.dailyKey(TODAY), "200", 200.0);
            masterRedisTemplate.opsForZSet().add(RankingKeys.dailyKey(TOMORROW), "200", 100.0);

            // act
            scoreCarryOverScheduler.carryOver();

            // assert
            Double score100 = masterRedisTemplate.opsForZSet().score(RankingKeys.dailyKey(TOMORROW), "100");
            Double score200 = masterRedisTemplate.opsForZSet().score(RankingKeys.dailyKey(TOMORROW), "200");
            assertThat(score100).isNotNull().isCloseTo(40.0, within(0.001));   // 400 × 0.1
            assertThat(score200).isNotNull().isCloseTo(120.0, within(0.001));  // 100 + (200 × 0.1)
        }
    }

    @DisplayName("TTL 설정")
    @Nested
    class TtlSetting {

        @Test
        @DisplayName("carry-over 후 내일 ZSET에 TTL이 설정된다")
        void setsTtlOnTomorrowZSet_afterCarryOver() {
            // arrange
            masterRedisTemplate.opsForZSet().add(RankingKeys.dailyKey(TODAY), "100", 100.0);

            // act
            scoreCarryOverScheduler.carryOver();

            // assert
            long ttlSeconds = masterRedisTemplate.getExpire(RankingKeys.dailyKey(TOMORROW), TimeUnit.SECONDS);
            long expectedMaxTtl = RankingKeys.dailyTtl(TOMORROW).toSeconds();
            assertThat(ttlSeconds)
                    .isGreaterThan(0)
                    .isLessThanOrEqualTo(expectedMaxTtl + 1);
        }
    }
}
