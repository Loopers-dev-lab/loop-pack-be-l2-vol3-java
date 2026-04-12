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

    // NOTE: commerce-api 모듈의 RankingRedisRepository와 동일한 KEY_PREFIX 사용. 변경 시 양쪽 동기화 필요.
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

    @Override
    public void carryOver(String sourceKey, String destKey, double carryWeight) {
        String source = KEY_PREFIX + sourceKey;
        String dest = KEY_PREFIX + destKey;
        redisTemplate.opsForZSet().unionAndStore(dest, java.util.List.of(source), dest,
                org.springframework.data.redis.connection.zset.Aggregate.SUM,
                org.springframework.data.redis.connection.zset.Weights.of(1.0, carryWeight));
        redisTemplate.expire(dest, TTL);
    }
}
