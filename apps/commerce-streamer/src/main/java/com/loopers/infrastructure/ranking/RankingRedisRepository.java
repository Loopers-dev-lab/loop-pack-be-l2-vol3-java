package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Repository
public class RankingRedisRepository implements RankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final long TTL_DAYS = 2;

    private final RedisTemplate<String, String> masterRedisTemplate;

    public RankingRedisRepository(
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Override
    public void incrementScore(String dateKey, Long productId, double score) {
        String key = KEY_PREFIX + dateKey;
        masterRedisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), score);
        masterRedisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);
    }

    @Override
    public void carryOverScores(String fromDateKey, String toDateKey, double weight) {
        String fromKey = KEY_PREFIX + fromDateKey;
        String toKey = KEY_PREFIX + toDateKey;

        byte[] destKey = toKey.getBytes(StandardCharsets.UTF_8);
        byte[] srcKey = fromKey.getBytes(StandardCharsets.UTF_8);
        byte[] weightsArg = String.valueOf(weight).getBytes(StandardCharsets.UTF_8);

        masterRedisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.execute("ZUNIONSTORE", destKey,
                "1".getBytes(StandardCharsets.UTF_8), srcKey,
                "WEIGHTS".getBytes(StandardCharsets.UTF_8), weightsArg);
            return null;
        });

        masterRedisTemplate.expire(toKey, TTL_DAYS, TimeUnit.DAYS);
    }
}
