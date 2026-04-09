package com.loopers.infrastructure.ranking.redis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingKeyConstants;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingScore;

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

    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingRepository(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void incrementScores(String key, List<RankingScore> scores) {
        if (scores.isEmpty()) {
            return;
        }

        byte[] rawKey = Objects.requireNonNull(redisTemplate.getStringSerializer().serialize(key));
        long ttlSeconds = RankingKeyConstants.calculateTtlSeconds(key);

        executeScorePipeline(scores, rawKey, ttlSeconds);
    }

    @Override
    public boolean exists(String key) {
        return redisTemplate.hasKey(key);
    }

    @Override
    public List<RankingScore> readTopScores(String key, int count) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, (long) count - 1);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<RankingScore> result = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            result.add(new RankingScore(
                    Long.parseLong(Objects.requireNonNull(tuple.getValue())),
                    Objects.requireNonNull(tuple.getScore())
            ));
        }
        return result;
    }

    @Override
    public void addScores(String key, List<RankingScore> scores, long ttlSeconds) {
        if (scores.isEmpty()) {
            return;
        }

        byte[] rawKey = Objects.requireNonNull(redisTemplate.getStringSerializer().serialize(key));

        executeScorePipeline(scores, rawKey, ttlSeconds);
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

    private void executeScorePipeline(List<RankingScore> scores, byte[] rawKey, long ttlSeconds) {
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (RankingScore score : scores) {
                byte[] member = Objects.requireNonNull(
                        redisTemplate.getStringSerializer().serialize(String.valueOf(score.productId()))
                );
                connection.zSetCommands().zIncrBy(rawKey, score.score(), member);
            }
            connection.keyCommands().expire(rawKey, ttlSeconds);
            return null;
        });
    }
}
