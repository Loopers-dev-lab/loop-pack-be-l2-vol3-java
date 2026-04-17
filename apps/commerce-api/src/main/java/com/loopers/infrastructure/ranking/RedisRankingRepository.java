package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RankingKeyBuilder;
import com.loopers.domain.repository.RankingRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
public class RedisRankingRepository implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<RankedProduct> getTopRankings(LocalDate date, int offset, int size) {
        String key = RankingKeyBuilder.buildKey(date);
        long start = offset;
        long end = (long) offset + size - 1;

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<RankedProduct> results = new ArrayList<>();
        long rank = offset;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long productId = Long.valueOf(tuple.getValue());
            double score = tuple.getScore() != null ? tuple.getScore() : 0.0;
            results.add(new RankedProduct(productId, score, rank));
            rank++;
        }
        return results;
    }

    @Override
    public long getTotalCount(LocalDate date) {
        String key = RankingKeyBuilder.buildKey(date);
        Long count = redisTemplate.opsForZSet().zCard(key);
        return count != null ? count : 0;
    }

    @Override
    public Long getRank(LocalDate date, Long productId) {
        String key = RankingKeyBuilder.buildKey(date);
        return redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
    }

    @Override
    public Double getScore(LocalDate date, Long productId) {
        String key = RankingKeyBuilder.buildKey(date);
        return redisTemplate.opsForZSet().score(key, String.valueOf(productId));
    }
}
