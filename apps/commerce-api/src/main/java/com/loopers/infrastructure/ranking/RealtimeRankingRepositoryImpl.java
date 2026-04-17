package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RankingKeys;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RealtimeRankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class RealtimeRankingRepositoryImpl implements RealtimeRankingRepository {

    private final RedisTemplate<String, String> defaultRedisTemplate;

    @Override
    public List<RankingEntry> findDailyRanking(LocalDate date, long offset, long size) {
        Set<ZSetOperations.TypedTuple<String>> result =
                defaultRedisTemplate.opsForZSet().reverseRangeWithScores(RankingKeys.dailyKey(date), offset, offset + size - 1);
        if (result == null) return Collections.emptyList();
        return result.stream()
                .map(t -> new RankingEntry(Long.parseLong(t.getValue()), t.getScore()))
                .toList();
    }

    @Override
    public long countDailyRanking(LocalDate date) {
        Long count = defaultRedisTemplate.opsForZSet().size(RankingKeys.dailyKey(date));
        return count != null ? count : 0L;
    }

    @Override
    public Long findProductDailyRank(LocalDate date, Long productId) {
        Long rank = defaultRedisTemplate.opsForZSet()
                .reverseRank(RankingKeys.dailyKey(date), String.valueOf(productId));
        return rank != null ? rank + 1 : null;
    }

    @Override
    public List<RankingEntry> findHourlyRanking(LocalDate date, int hour, long offset, long size) {
        Set<ZSetOperations.TypedTuple<String>> result =
                defaultRedisTemplate.opsForZSet().reverseRangeWithScores(RankingKeys.hourlyKey(date, hour), offset, offset + size - 1);
        if (result == null) return Collections.emptyList();
        return result.stream()
                .map(t -> new RankingEntry(Long.parseLong(t.getValue()), t.getScore()))
                .toList();
    }

    @Override
    public long countHourlyRanking(LocalDate date, int hour) {
        Long count = defaultRedisTemplate.opsForZSet().size(RankingKeys.hourlyKey(date, hour));
        return count != null ? count : 0L;
    }
}
