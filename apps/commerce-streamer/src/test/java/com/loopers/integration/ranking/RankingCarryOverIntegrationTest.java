package com.loopers.integration.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import com.loopers.application.ranking.RankingCarryOverScheduler;
import com.loopers.config.redis.RedisConfig;
import com.loopers.utils.RedisCleanUp;

@SpringBootTest
class RankingCarryOverIntegrationTest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String KEY_PREFIX = "ranking:v1:all:";

    @Autowired
    private RankingCarryOverScheduler rankingCarryOverScheduler;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("스코어 이월을 수행할 때,")
    @Nested
    class CarryOver {

        @DisplayName("오늘 상위 스코어가 감쇠되어 내일 키에 적재된다.")
        @Test
        void carriesOverDecayedScoresToTomorrowKey() {
            // arrange
            String todayKey = KEY_PREFIX + LocalDate.now().format(DATE_FORMAT);
            String tomorrowKey = KEY_PREFIX + LocalDate.now().plusDays(1).format(DATE_FORMAT);

            redisTemplate.opsForZSet().add(todayKey, "1", 100.0);
            redisTemplate.opsForZSet().add(todayKey, "2", 80.0);
            redisTemplate.opsForZSet().add(todayKey, "3", 60.0);

            // act
            rankingCarryOverScheduler.carryOver();

            // assert
            Set<ZSetOperations.TypedTuple<String>> tomorrowScores =
                    redisTemplate.opsForZSet().reverseRangeWithScores(tomorrowKey, 0, -1);

            assertThat(tomorrowScores).hasSize(3);
            assertThat(redisTemplate.opsForZSet().score(tomorrowKey, "1")).isCloseTo(1.0, offset(0.001));
            assertThat(redisTemplate.opsForZSet().score(tomorrowKey, "2")).isCloseTo(0.8, offset(0.001));
            assertThat(redisTemplate.opsForZSet().score(tomorrowKey, "3")).isCloseTo(0.6, offset(0.001));
        }

        @DisplayName("내일 키가 이미 존재하면 이월하지 않는다.")
        @Test
        void skips_whenTomorrowKeyAlreadyExists() {
            // arrange
            String todayKey = KEY_PREFIX + LocalDate.now().format(DATE_FORMAT);
            String tomorrowKey = KEY_PREFIX + LocalDate.now().plusDays(1).format(DATE_FORMAT);

            redisTemplate.opsForZSet().add(todayKey, "1", 100.0);
            redisTemplate.opsForZSet().add(tomorrowKey, "99", 1.0);

            // act
            rankingCarryOverScheduler.carryOver();

            // assert — 내일 키에 기존 데이터만 존재, 이월 없음
            assertThat(redisTemplate.opsForZSet().zCard(tomorrowKey)).isEqualTo(1);
            assertThat(redisTemplate.opsForZSet().score(tomorrowKey, "1")).isNull();
        }

        @DisplayName("내일 키에 TTL이 설정된다.")
        @Test
        void setsTtlOnTomorrowKey() {
            // arrange
            String todayKey = KEY_PREFIX + LocalDate.now().format(DATE_FORMAT);
            String tomorrowKey = KEY_PREFIX + LocalDate.now().plusDays(1).format(DATE_FORMAT);

            redisTemplate.opsForZSet().add(todayKey, "1", 100.0);

            // act
            rankingCarryOverScheduler.carryOver();

            // assert
            Long ttl = redisTemplate.getExpire(tomorrowKey);
            assertThat(ttl).isNotNull().isGreaterThan(172700L);
        }
    }
}
