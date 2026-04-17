package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class RankingRepositoryImpl implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRepositoryImpl(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<RankingEntry> getTopN(String key, int offset, int size) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
            redisTemplate.opsForZSet().reverseRangeWithScores(key, offset, (long) offset + size - 1);
        if (tuples == null) return List.of();
        List<RankingEntry> entries = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            entries.add(new RankingEntry(
                Long.valueOf(tuple.getValue()),
                tuple.getScore() != null ? tuple.getScore() : 0.0
            ));
        }
        return entries;
    }

    @Override
    public Long getRank(String key, Long productId) {
        return redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
    }

    @Override
    public Double getScore(String key, Long productId) {
        return redisTemplate.opsForZSet().score(key, String.valueOf(productId));
    }
}
