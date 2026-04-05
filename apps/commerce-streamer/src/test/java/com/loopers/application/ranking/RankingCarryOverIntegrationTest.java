package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingKeyGenerator;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(RedisTestContainersConfig.class)
@DisplayName("Carry-Over Scheduler 통합 테스트 — 콜드 스타트 완화 검증")
class RankingCarryOverIntegrationTest {

    @Autowired
    private RankingApp rankingApp;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("전날 ZSET → 내일 ZSET, weight=0.1로 carry-over됨")
    void carryOverAppliesWeight() {
        LocalDate today = LocalDate.of(2026, 4, 5);
        LocalDate tomorrow = today.plusDays(1);
        String todayKey = RankingKeyGenerator.dailyKey(today);
        String tomorrowKey = RankingKeyGenerator.dailyKey(tomorrow);

        // 오늘 ZSET: 상품1=10000, 상품2=5000
        redisTemplate.opsForZSet().add(todayKey, "1", 10000.0);
        redisTemplate.opsForZSet().add(todayKey, "2", 5000.0);

        // when
        long count = rankingApp.carryOver(today, tomorrow, 0.1);

        // then: 내일 ZSET에 weight 적용된 점수로 존재
        assertThat(count).isEqualTo(2L);
        assertThat(redisTemplate.opsForZSet().score(tomorrowKey, "1")).isEqualTo(1000.0);
        assertThat(redisTemplate.opsForZSet().score(tomorrowKey, "2")).isEqualTo(500.0);

        // 내일 Key에 TTL이 설정되어 있음
        Long ttl = redisTemplate.getExpire(tomorrowKey);
        assertThat(ttl).isGreaterThan(0L);
    }

    @Test
    @DisplayName("source ZSET이 없으면 0 반환 (no-op)")
    void noOpWhenSourceKeyMissing() {
        LocalDate today = LocalDate.of(2026, 4, 5);
        LocalDate tomorrow = today.plusDays(1);

        long count = rankingApp.carryOver(today, tomorrow, 0.1);

        assertThat(count).isZero();
    }

    @Test
    @DisplayName("carry-over 후 신규 ZINCRBY 이벤트는 기존 점수에 정상 누적된다 (9-3 검증)")
    void carryOverCoexistsWithNewEvents() {
        LocalDate today = LocalDate.of(2026, 4, 5);
        LocalDate tomorrow = today.plusDays(1);
        String todayKey = RankingKeyGenerator.dailyKey(today);
        String tomorrowKey = RankingKeyGenerator.dailyKey(tomorrow);

        // 오늘 ZSET에 10000점 설정
        redisTemplate.opsForZSet().add(todayKey, "1", 10000.0);

        // carry-over로 내일에 1000점 초기값 설정
        rankingApp.carryOver(today, tomorrow, 0.1);
        assertThat(redisTemplate.opsForZSet().score(tomorrowKey, "1")).isEqualTo(1000.0);

        // 내일(자정 이후)에 새 이벤트: 주문 1건 (7000점 추가) → ZINCRBY 누적
        rankingApp.applyOrderScore(1L, new java.math.BigDecimal("10000"), 1, tomorrow);

        // 결과: 1000(carry-over) + 7000(주문) = 8000
        assertThat(redisTemplate.opsForZSet().score(tomorrowKey, "1")).isEqualTo(8000.0);
    }
}
