package com.loopers.application.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.domain.ranking.RankingWriter;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Carry-Over 스케줄러 통합 테스트 — 오늘 ZSET 데이터를 내일 키로 1% 시드.
 */
@SpringBootTest
@DisplayName("RankingCarryOverScheduler 통합 테스트")
class RankingCarryOverSchedulerIntegrationTest {

    @Autowired
    private RankingCarryOverScheduler scheduler;

    @Autowired
    private RankingWriter rankingWriter;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> masterRedisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("오늘 키의 점수가 내일 키에 1% 가중으로 시드된다")
    void carryOverSeeds() {
        // given
        LocalDate today = LocalDate.of(2026, 4, 9);
        String todayKey = RankingKey.daily(today);
        String tomorrowKey = RankingKey.daily(today.plusDays(1));
        rankingWriter.upsertScore(todayKey, 1L, 100.0);
        rankingWriter.upsertScore(todayKey, 2L, 50.0);

        // when
        scheduler.carryOverFor(today);

        // then
        Double score1 = masterRedisTemplate.opsForZSet().score(tomorrowKey, "1");
        Double score2 = masterRedisTemplate.opsForZSet().score(tomorrowKey, "2");
        assertThat(score1).isNotNull().isCloseTo(1.0, within(1e-9));
        assertThat(score2).isNotNull().isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("오늘 키가 비어 있으면 no-op — 내일 키도 생성되지 않는다")
    void noopWhenTodayEmpty() {
        // given
        LocalDate today = LocalDate.of(2026, 4, 9);
        String tomorrowKey = RankingKey.daily(today.plusDays(1));

        // when
        scheduler.carryOverFor(today);

        // then
        Long total = masterRedisTemplate.opsForZSet().zCard(tomorrowKey);
        assertThat(total == null || total == 0L).isTrue();
    }

    @Test
    @DisplayName("두 번 실행해도 결과가 동일 (idempotent — ZADD 덮어쓰기)")
    void idempotent() {
        // given
        LocalDate today = LocalDate.of(2026, 4, 9);
        String todayKey = RankingKey.daily(today);
        rankingWriter.upsertScore(todayKey, 1L, 100.0);

        // when
        scheduler.carryOverFor(today);
        scheduler.carryOverFor(today);

        // then
        String tomorrowKey = RankingKey.daily(today.plusDays(1));
        Double score = masterRedisTemplate.opsForZSet().score(tomorrowKey, "1");
        assertThat(score).isCloseTo(1.0, within(1e-9));
    }
}
