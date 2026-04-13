package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class RedisRankingRepository implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public List<RankingEntry> getTopRankings(String key, long start, long end) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
            redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<RankingEntry> entries = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long productId = Long.parseLong(tuple.getValue());
            double score = tuple.getScore() != null ? tuple.getScore() : 0.0;
            entries.add(new RankingEntry(productId, score));
        }
        return entries;
    }

    @Override
    public Long getRank(String key, Long productId) {
        return redisTemplate.opsForZSet().reverseRank(key, productId.toString());
    }

    @Override
    public Double getScore(String key, Long productId) {
        return redisTemplate.opsForZSet().score(key, productId.toString());
    }
}
