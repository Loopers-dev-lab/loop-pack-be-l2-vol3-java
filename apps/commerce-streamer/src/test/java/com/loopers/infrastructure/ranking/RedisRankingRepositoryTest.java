package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withPrecision;

@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@SpringBootTest(properties = "spring.kafka.consumer.auto-startup=false")
class RedisRankingRepositoryTest {

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("incrementScore() 를 호출할 때, ")
    @Nested
    class IncrementScore {

        @DisplayName("ZSET 에 productId 의 score 가 누적된다.")
        @Test
        void accumulatesScore_inZSet() {
            // arrange
            LocalDate date = LocalDate.of(2026, 4, 8);

            // act
            rankingRepository.incrementScore(42L, date, 0.1);
            rankingRepository.incrementScore(42L, date, 0.2);

            // assert
            Double score = redisTemplate.opsForZSet().score("ranking:all:20260408", "42");
            assertThat(score).isEqualTo(0.3, withPrecision(0.0001));
        }

        @DisplayName("서로 다른 상품은 각자의 score 가 쌓인다.")
        @Test
        void accumulatesScoreSeparately_forDifferentProducts() {
            // arrange
            LocalDate date = LocalDate.of(2026, 4, 8);

            // act
            rankingRepository.incrementScore(42L, date, 0.1);
            rankingRepository.incrementScore(99L, date, 0.5);

            // assert
            Double scoreA = redisTemplate.opsForZSet().score("ranking:all:20260408", "42");
            Double scoreB = redisTemplate.opsForZSet().score("ranking:all:20260408", "99");
            assertThat(scoreA).isEqualTo(0.1, withPrecision(0.0001));
            assertThat(scoreB).isEqualTo(0.5, withPrecision(0.0001));
        }

        @DisplayName("TTL 이 key 날짜 기준 2일 후로 설정된다.")
        @Test
        void setsTtl_twoDaysAfterKeyDate() {
            // arrange
            LocalDate date = LocalDate.of(2026, 4, 8);

            // act
            rankingRepository.incrementScore(42L, date, 0.1);

            // assert
            Long ttlSeconds = redisTemplate.getExpire("ranking:all:20260408", TimeUnit.SECONDS);
            assertThat(ttlSeconds).isNotNull().isPositive();
            // key 날짜 + 2일 = 2026-04-10 00:00:00 UTC 까지. TTL은 최대 2일(172800초) 이내
            assertThat(ttlSeconds).isLessThanOrEqualTo(172800L);
        }
    }
}
