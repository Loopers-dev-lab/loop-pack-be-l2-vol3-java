package com.loopers.application.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.infrastructure.ranking.RankingRedisUpdater;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RankingCarryOverScheduler {

    private static final double CARRY_OVER_WEIGHT = 0.1;
    private static final long TTL_SECONDS = 172800;

    private final RedisTemplate<String, String> redisTemplate;
    private final RankingRedisUpdater rankingRedisUpdater;

    public RankingCarryOverScheduler(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
            RankingRedisUpdater rankingRedisUpdater) {
        this.redisTemplate = redisTemplate;
        this.rankingRedisUpdater = rankingRedisUpdater;
    }

    @Scheduled(cron = "0 50 23 * * *")
    public void carryOver() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        String todayKey = rankingRedisUpdater.allKeyOf(today);
        String tomorrowKey = rankingRedisUpdater.allKeyOf(tomorrow);

        Set<TypedTuple<String>> todayScores = redisTemplate.opsForZSet()
                .rangeWithScores(todayKey, 0, -1);
        if (todayScores == null || todayScores.isEmpty()) {
            log.info("[Ranking] carry-over 스킵 — 오늘({}) 데이터 없음", todayKey);
            return;
        }

        for (TypedTuple<String> tuple : todayScores) {
            double carriedScore = (tuple.getScore() != null ? tuple.getScore() : 0) * CARRY_OVER_WEIGHT;
            redisTemplate.opsForZSet()
                    .addIfAbsent(tomorrowKey, tuple.getValue(), carriedScore);
        }
        redisTemplate.expire(tomorrowKey, Duration.ofSeconds(TTL_SECONDS));

        Long tomorrowSize = redisTemplate.opsForZSet().zCard(tomorrowKey);
        log.info("[Ranking] carry-over 완료 — {} → {} ({}건, weight={})",
                todayKey, tomorrowKey, tomorrowSize, CARRY_OVER_WEIGHT);
    }
}
