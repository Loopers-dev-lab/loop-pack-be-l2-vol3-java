package com.loopers.application.ranking;

import com.loopers.config.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class RankingAppService {

    private static final String ALL_KEY_PREFIX = "ranking:all:";
    private static final String HOURLY_KEY_PREFIX = "ranking:hourly:";

    private final StringRedisTemplate redisTemplate;

    public RankingAppService(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public List<RankingEntry> getTopRankings(String date, int page, int size) {
        return fetchFromZSet(ALL_KEY_PREFIX + date, page, size);
    }

    @Transactional(readOnly = true)
    public List<RankingEntry> getHourlyTopRankings(String hour, int page, int size) {
        return fetchFromZSet(HOURLY_KEY_PREFIX + hour, page, size);
    }

    @Transactional(readOnly = true)
    public Long getProductRank(String date, Long productId) {
        String key = ALL_KEY_PREFIX + date;
        Long rank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
        return rank != null ? rank + 1 : null;
    }

    private List<RankingEntry> fetchFromZSet(String key, int page, int size) {
        long start = (long) Math.max(page, 0) * size;
        long end = start + size - 1;

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<RankingEntry> entries = new ArrayList<>();
        int rank = (int) start + 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            entries.add(new RankingEntry(rank++, Long.valueOf(tuple.getValue()), tuple.getScore()));
        }
        return entries;
    }
}
