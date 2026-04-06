package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RankingRedisRepository implements RankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final Duration TTL = Duration.ofDays(2);

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRedisRepository(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void incrementScore(String dateKey, Long productId, double score) {
        String key = KEY_PREFIX + dateKey;
        redisTemplate.opsForZSet().incrementScore(key, productId.toString(), score);
        redisTemplate.expire(key, TTL);
    }
}
