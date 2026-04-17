package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class RankingRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public List<RankEntry> getRankings(String zsetKey, int page, int size) {
        long start = (long) page * size;
        long end = start + size - 1;

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(zsetKey, start, end);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<RankEntry> result = new ArrayList<>();
        int rank = (int) start + 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            result.add(new RankEntry(
                    Long.parseLong(tuple.getValue()),
                    tuple.getScore(),
                    rank++
            ));
        }
        return result;
    }

    public Long getTotalCount(String zsetKey) {
        return redisTemplate.opsForZSet().zCard(zsetKey);
    }

    public Integer getRank(String zsetKey, Long productId) {
        Long rank = redisTemplate.opsForZSet()
                .reverseRank(zsetKey, String.valueOf(productId));
        return rank == null ? null : rank.intValue() + 1;
    }

    public Double getScore(String zsetKey, Long productId) {
        return redisTemplate.opsForZSet()
                .score(zsetKey, String.valueOf(productId));
    }
}
