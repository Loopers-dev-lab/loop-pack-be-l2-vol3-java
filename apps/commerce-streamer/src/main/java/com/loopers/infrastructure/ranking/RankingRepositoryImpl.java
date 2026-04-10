package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class RankingRepositoryImpl implements RankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final Duration TTL = Duration.ofDays(2);

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRepositoryImpl(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void incrementScore(String date, Long productId, double delta) {
        String key = KEY_PREFIX + date;
        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), delta);

        // 최초 1회 TTL 설정
        Long expire = redisTemplate.getExpire(key);
        if (expire != null && expire == -1L) {
            redisTemplate.expire(key, TTL);
        }
    }
}
