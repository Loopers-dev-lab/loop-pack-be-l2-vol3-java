package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingKeyGenerator;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class RankingRedisRepository implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<RankingEntry> findTopN(LocalDate date, long offset, long size) {
        String key = RankingKeyGenerator.dailyKey(date);
        long start = offset;
        long end = offset + size - 1;

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<RankingEntry> result = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String member = tuple.getValue();
            Double score = tuple.getScore();
            if (member == null || score == null) {
                continue;
            }
            result.add(new RankingEntry(Long.parseLong(member), score));
        }
        return result;
    }

    @Override
    public List<RankingEntry> findByCursor(LocalDate date, Double cursorScore, long size) {
        String key = RankingKeyGenerator.dailyKey(date);
        double max = cursorScore != null ? cursorScore : Double.POSITIVE_INFINITY;
        long fetchSize = cursorScore != null ? size + 1 : size;

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                        key, Double.NEGATIVE_INFINITY, max, 0, fetchSize);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<RankingEntry> result = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String member = tuple.getValue();
            Double score = tuple.getScore();
            if (member == null || score == null) {
                continue;
            }
            if (cursorScore != null && score >= cursorScore) {
                continue;
            }
            result.add(new RankingEntry(Long.parseLong(member), score));
            if (result.size() >= size) {
                break;
            }
        }
        return result;
    }

    @Override
    public Optional<Long> findRank(LocalDate date, Long productDbId) {
        String key = RankingKeyGenerator.dailyKey(date);
        Long rank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productDbId));
        if (rank == null) {
            return Optional.empty();
        }
        return Optional.of(rank + 1);
    }

    @Override
    public Optional<Double> findScore(LocalDate date, Long productDbId) {
        String key = RankingKeyGenerator.dailyKey(date);
        Double score = redisTemplate.opsForZSet().score(key, String.valueOf(productDbId));
        return Optional.ofNullable(score);
    }

    @Override
    public long countMembers(LocalDate date) {
        String key = RankingKeyGenerator.dailyKey(date);
        Long size = redisTemplate.opsForZSet().zCard(key);
        return size != null ? size : 0L;
    }
}
