package com.loopers.infrastructure.ranking.repository;

import com.loopers.domain.ranking.repository.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class RankingRepositoryImpl implements RankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public List<RankingEntry> getTopRankings(String date, int offset, int size) {
        String key = KEY_PREFIX + date;
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, offset, (long) offset + size - 1);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<RankingEntry> entries = new ArrayList<>();
        int rank = offset + 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String value = tuple.getValue();
            if (value == null) continue;
            Long productId = Long.valueOf(value);
            double score = tuple.getScore() != null ? tuple.getScore() : 0.0;
            entries.add(new RankingEntry(productId, score, rank++));
        }
        return entries;
    }

    @Override
    public long getTotalCount(String date) {
        String key = KEY_PREFIX + date;
        Long count = redisTemplate.opsForZSet().zCard(key);
        return count != null ? count : 0;
    }

    @Override
    public RankingEntry getProductRanking(String date, Long productId) {
        String key = KEY_PREFIX + date;
        String member = String.valueOf(productId);
        Long rank = redisTemplate.opsForZSet().reverseRank(key, member);
        Double score = redisTemplate.opsForZSet().score(key, member);
        if (rank == null || score == null) {
            return null;
        }
        return new RankingEntry(productId, score, rank + 1);
    }
}
