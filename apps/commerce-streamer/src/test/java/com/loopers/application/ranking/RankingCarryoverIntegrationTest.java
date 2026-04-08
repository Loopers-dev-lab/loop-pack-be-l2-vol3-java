package com.loopers.application.ranking;

import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "collector.ranking-carryover.enabled=true",
        "collector.ranking-carryover.zone=Asia/Seoul",
        "collector.ranking-carryover.top-n=2",
        "collector.ranking-carryover.weight=0.1",
        "collector.ranking-carryover.only-when-today-empty=true",
        "spring.batch.job.enabled=false"
})
@Import({RedisTestContainersConfig.class})
class RankingCarryoverIntegrationTest {

    @Autowired
    private RankingCarryoverService carryoverService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Test
    @DisplayName("carryover는 전일 Top N을 감쇠해 당일 키에 시드한다(당일 키가 비어 있을 때만).")
    void carryover_shouldSeedTodayFromYesterdayTopN() {
        redisCleanUp.truncateAll();
        LocalDate today = LocalDate.of(2026, 4, 8);
        String yesterdayKey = "ranking:all:20260407";
        String todayKey = "ranking:all:20260408";

        redisTemplate.opsForZSet().add(yesterdayKey, "101", 10.0d);
        redisTemplate.opsForZSet().add(yesterdayKey, "102", 5.0d);
        redisTemplate.opsForZSet().add(yesterdayKey, "103", 1.0d);

        RankingCarryoverProperties props = new RankingCarryoverProperties(
                true,
                "0 0 0 * * ?",
                "Asia/Seoul",
                2,
                0.1d,
                true
        );

        carryoverService.carryover(today, props);

        assertThat(redisTemplate.opsForZSet().size(todayKey)).isEqualTo(2L);
        assertThat(redisTemplate.opsForZSet().score(todayKey, "101")).isEqualTo(1.0d);
        assertThat(redisTemplate.opsForZSet().score(todayKey, "102")).isEqualTo(0.5d);
        assertThat(redisTemplate.opsForZSet().score(todayKey, "103")).isNull();
    }
}

