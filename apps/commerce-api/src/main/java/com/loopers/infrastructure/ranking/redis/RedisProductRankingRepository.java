package com.loopers.infrastructure.ranking.redis;

import com.loopers.application.ranking.RankingProductView;
import com.loopers.application.ranking.RankingRepository;
import com.loopers.config.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class RedisProductRankingRepository implements RankingRepository {

    private static final DateTimeFormatter KEY_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final RedisTemplate<String, String> redisTemplate;

    public RedisProductRankingRepository(@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<RankingProductView> findTop(LocalDate metricDate, int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(buildDailyRankingKey(metricDate), 0, limit - 1L);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        AtomicLong rank = new AtomicLong(1L);
        return tuples.stream()
                .map(tuple -> new RankingProductView(
                        UUID.fromString(tuple.getValue()),
                        rank.getAndIncrement(),
                        tuple.getScore()
                ))
                .toList();
    }

    @Override
    public RankingProductView findProductRank(LocalDate metricDate, UUID productId) {
        String member = productId.toString();
        Long rank = redisTemplate.opsForZSet().reverseRank(buildDailyRankingKey(metricDate), member);
        if (rank == null) {
            return null;
        }
        Double score = redisTemplate.opsForZSet().score(buildDailyRankingKey(metricDate), member);
        return new RankingProductView(productId, rank + 1L, score);
    }

    public String buildDailyRankingKey(LocalDate metricDate) {
        return "ranking:all:" + metricDate.format(KEY_DATE_FORMATTER);
    }
}
