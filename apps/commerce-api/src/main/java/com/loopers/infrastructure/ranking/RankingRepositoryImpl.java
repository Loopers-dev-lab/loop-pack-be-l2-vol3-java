package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

@Repository
public class RankingRepositoryImpl implements RankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long TTL_DAYS = 2;

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, String> masterRedisTemplate;

    public RankingRepositoryImpl(
            RedisTemplate<String, String> redisTemplate,
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Override
    public List<RankedProduct> getTopN(LocalDate date, int size, int page) {
        String key = KEY_PREFIX + date.format(DATE_FORMATTER);
        long start = (long) page * size;
        long end = start + size - 1;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (tuples == null) {
            return List.of();
        }
        return tuples.stream()
            .map(t -> new RankedProduct(Long.parseLong(t.getValue()), t.getScore()))
            .toList();
    }

    @Override
    public Long getRank(Long productId, LocalDate date) {
        String key = KEY_PREFIX + date.format(DATE_FORMATTER);
        Long rank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
        return rank == null ? null : rank + 1;
    }

    @Override
    public void carryOver(LocalDate from, LocalDate to, double ratio) {
        String fromKey = KEY_PREFIX + from.format(DATE_FORMATTER);
        String toKey = KEY_PREFIX + to.format(DATE_FORMATTER);
        masterRedisTemplate.opsForZSet().unionAndStore(
            fromKey,
            Collections.emptyList(),
            toKey,
            Aggregate.SUM,
            Weights.of(ratio)
        );
        masterRedisTemplate.expire(toKey, TTL_DAYS, TimeUnit.DAYS);
    }
}
