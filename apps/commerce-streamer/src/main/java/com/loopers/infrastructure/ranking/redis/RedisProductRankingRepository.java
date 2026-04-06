package com.loopers.infrastructure.ranking.redis;

import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.ProductRankingRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis Sorted Set 기반 상품 랭킹 적재 구현체.
 *
 * <p>Pipeline으로 ZINCRBY와 EXPIRE를 일괄 실행하여
 * 네트워크 왕복을 최소화한다. TTL은 2일(172800초).</p>
 */
@Slf4j
@Repository
public class RedisProductRankingRepository implements ProductRankingRepository {

    private static final long TTL_SECONDS = 172800;

    private final RedisTemplate<String, String> redisTemplate;

    public RedisProductRankingRepository(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void incrementScores(String key, Map<Long, Double> productScores) {
        if (productScores.isEmpty()) {
            return;
        }

        byte[] rawKey = Objects.requireNonNull(redisTemplate.getStringSerializer().serialize(key));

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Map.Entry<Long, Double> entry : productScores.entrySet()) {
                byte[] member = Objects.requireNonNull(
                        redisTemplate.getStringSerializer().serialize(String.valueOf(entry.getKey()))
                );
                connection.zSetCommands().zIncrBy(rawKey, entry.getValue(), member);
            }
            connection.keyCommands().expire(rawKey, TTL_SECONDS);
            return null;
        });
    }
}
