package com.loopers.infrastructure.ranking.redis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis Sorted Set 기반 상품 랭킹 구현체.
 *
 * <p>실시간 점수 적재와 스코어 이월을 모두 처리한다.
 * Pipeline으로 ZINCRBY와 EXPIRE를 한 번의 네트워크 왕복으로
 * 일괄 실행하여 성능을 최적화한다.</p>
 */
@Slf4j
@Repository
public class RedisRankingRepository implements RankingRepository {

    private static final long TTL_SECONDS = 172800;

    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingRepository(
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

    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public Map<String, Double> readTopScores(String key, int count) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, (long) count - 1);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Double> result = new LinkedHashMap<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            result.put(
                    Objects.requireNonNull(tuple.getValue()),
                    Objects.requireNonNull(tuple.getScore())
            );
        }
        return result;
    }

    @Override
    public void addScores(String key, Map<String, Double> scores, long ttlSeconds) {
        if (scores.isEmpty()) {
            return;
        }

        byte[] rawKey = Objects.requireNonNull(redisTemplate.getStringSerializer().serialize(key));

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Map.Entry<String, Double> entry : scores.entrySet()) {
                byte[] member = Objects.requireNonNull(
                        redisTemplate.getStringSerializer().serialize(entry.getKey())
                );
                connection.zSetCommands().zIncrBy(rawKey, entry.getValue(), member);
            }
            connection.keyCommands().expire(rawKey, ttlSeconds);
            return null;
        });
    }

    @Override
    public void removeMembers(String key, List<Long> productIds) {
        if (productIds.isEmpty()) {
            return;
        }

        byte[][] members = productIds.stream()
                .map(id -> Objects.requireNonNull(
                        redisTemplate.getStringSerializer().serialize(String.valueOf(id))
                ))
                .toArray(byte[][]::new);

        byte[] rawKey = Objects.requireNonNull(redisTemplate.getStringSerializer().serialize(key));
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            connection.zSetCommands().zRem(rawKey, members);
            return null;
        });
    }
}
