package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Repository
public class RankingRedisRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration TTL = Duration.ofDays(2);

    /**
     * ZINCRBY 후 score < 0이면 0으로 고정하는 Lua Script (원자적 처리).
     * KEYS[1] = ranking key, ARGV[1] = increment, ARGV[2] = member
     */
    private static final RedisScript<Void> ZINCRBY_WITH_FLOOR = RedisScript.of(
        "local score = redis.call('ZINCRBY', KEYS[1], ARGV[1], ARGV[2]) " +
        "if tonumber(score) < 0 then " +
        "  redis.call('ZADD', KEYS[1], 0, ARGV[2]) " +
        "end",
        Void.class
    );

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRedisRepository(@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void incrementScore(LocalDate date, Long productId, double increment) {
        String key = buildKey(date);
        redisTemplate.execute(
            ZINCRBY_WITH_FLOOR,
            List.of(key),
            String.valueOf(increment),
            String.valueOf(productId)
        );
        redisTemplate.expire(key, TTL);
    }

    public static String buildKey(LocalDate date) {
        return KEY_PREFIX + date.format(DATE_FORMATTER);
    }
}
