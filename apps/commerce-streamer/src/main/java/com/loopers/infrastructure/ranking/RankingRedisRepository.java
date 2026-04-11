package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RankingRedisRepository implements RankingRepository {

    private final RedisTemplate<String, String> masterRedisTemplate;

    public RankingRedisRepository(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Override
    public void incrementScore(String key, Long productId, double score) {
        masterRedisTemplate.opsForZSet()
                .incrementScore(key, String.valueOf(productId), score);
    }

    @Override
    public void setTtl(String key, long seconds) {
        masterRedisTemplate.expire(key, seconds, TimeUnit.SECONDS);
    }
}
