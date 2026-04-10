package com.loopers.infrastructure.ranking;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class RankingRedisRepository {

    private static final String RANKING_ZSET_PREFIX = "ranking:all:";
    private static final String RANKING_WEEKLY_PREFIX = "ranking:weekly:";
    private static final String RANKING_MONTHLY_PREFIX = "ranking:monthly:";

    private final RedisTemplate<String, String> readTemplate;

    public RankingRedisRepository(RedisTemplate<String, String> readTemplate) {
        this.readTemplate = readTemplate;
    }

    public List<RankingEntry> getTopN(String date, long start, long end) {
        return getTopN(RANKING_ZSET_PREFIX, date, start, end);
    }

    public List<RankingEntry> getTopN(String prefix, String date, long start, long end) {
        String key = prefix + date;
        Set<TypedTuple<String>> tuples = readTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (tuples == null) return Collections.emptyList();
        List<RankingEntry> entries = new ArrayList<>(tuples.size());
        for (TypedTuple<String> tuple : tuples) {
            entries.add(new RankingEntry(Long.parseLong(tuple.getValue()), tuple.getScore()));
        }
        return entries;
    }

    public RankAndScore getRankAndScore(String date, Long productId) {
        return getRankAndScore(RANKING_ZSET_PREFIX, date, productId);
    }

    public RankAndScore getRankAndScore(String prefix, String date, Long productId) {
        String key = prefix + date;
        String member = String.valueOf(productId);
        Long rank = readTemplate.opsForZSet().reverseRank(key, member);
        if (rank == null) return null;
        Double score = readTemplate.opsForZSet().score(key, member);
        return new RankAndScore(rank + 1, score != null ? score : 0.0);
    }

    public long getTotalCount(String date) {
        return getTotalCount(RANKING_ZSET_PREFIX, date);
    }

    public long getTotalCount(String prefix, String date) {
        String key = prefix + date;
        Long count = readTemplate.opsForZSet().zCard(key);
        return count != null ? count : 0;
    }

    public record RankingEntry(Long productId, double score) {}

    public record RankAndScore(long rank, double score) {}
}
