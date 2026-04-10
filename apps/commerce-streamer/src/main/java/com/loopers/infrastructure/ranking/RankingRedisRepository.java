package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Set;

@Slf4j
@Repository
public class RankingRedisRepository {

    private static final Duration RANKING_TTL = Duration.ofDays(2);

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRedisRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    public void incrementScore(String key, Long productId, double score) {
        try {
            redisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), score);
            redisTemplate.expire(key, RANKING_TTL);
        } catch (Exception e) {
            log.warn("랭킹 점수 갱신 실패: key={}, productId={}, error={}", key, productId, e.getMessage());
        }
    }

    public Set<ZSetOperations.TypedTuple<String>> getTopN(String key, int offset, int size) {
        return redisTemplate.opsForZSet().reverseRangeWithScores(key, offset, (long) offset + size - 1);
    }

    public Long getRank(String key, Long productId) {
        return redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
    }

    public Double getScore(String key, Long productId) {
        return redisTemplate.opsForZSet().score(key, String.valueOf(productId));
    }
}
