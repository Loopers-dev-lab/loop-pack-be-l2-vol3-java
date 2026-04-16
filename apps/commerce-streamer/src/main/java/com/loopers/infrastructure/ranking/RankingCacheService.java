package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

@Component
public class RankingCacheService {

    static final String KEY_PREFIX = "ranking:all:";
    static final Duration TTL = Duration.ofDays(2);
    private static final double CARRY_OVER_WEIGHT = 0.1;

    private final RedisTemplate<String, String> masterRedisTemplate;

    public RankingCacheService(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    public void incrementScore(String date, Long productId, double score) {
        String key = KEY_PREFIX + date;
        masterRedisTemplate.opsForZSet().incrementScore(key, productId.toString(), score);
        masterRedisTemplate.expire(key, TTL);
    }

    public void carryOver(String yesterdayDate, String todayDate) {
        String yesterdayKey = KEY_PREFIX + yesterdayDate;
        String todayKey = KEY_PREFIX + todayDate;

        Boolean yesterdayExists = masterRedisTemplate.hasKey(yesterdayKey);
        if (Boolean.FALSE.equals(yesterdayExists)) {
            return;
        }

        masterRedisTemplate.opsForZSet().unionAndStore(
            yesterdayKey,
            Collections.emptyList(),
            todayKey,
            Aggregate.SUM,
            Weights.of(CARRY_OVER_WEIGHT)
        );
        masterRedisTemplate.expire(todayKey, TTL);
    }
}
