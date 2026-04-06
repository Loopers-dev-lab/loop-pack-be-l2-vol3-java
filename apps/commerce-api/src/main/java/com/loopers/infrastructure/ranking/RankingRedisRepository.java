package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class RankingRedisRepository implements RankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<ProductScore> getTopRankings(String dateKey, long offset, long size) {
        String key = KEY_PREFIX + dateKey;
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, offset, offset + size - 1);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<ProductScore> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long productId = Long.valueOf(tuple.getValue());
            double score = tuple.getScore();
            result.add(new ProductScore(productId, score));
        }
        return result;
    }

    @Override
    public Long getRank(String dateKey, Long productId) {
        String key = KEY_PREFIX + dateKey;
        Long rank = redisTemplate.opsForZSet().reverseRank(key, productId.toString());
        return rank != null ? rank + 1 : null;
    }

    @Override
    public Double getScore(String dateKey, Long productId) {
        String key = KEY_PREFIX + dateKey;
        return redisTemplate.opsForZSet().score(key, productId.toString());
    }
}
