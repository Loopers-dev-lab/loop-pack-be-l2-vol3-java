package com.loopers.infrastructure.ranking.redis;

import com.loopers.application.ranking.RankingProductView;
import com.loopers.application.ranking.RankingRepository;
import com.loopers.config.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class RedisProductRankingRepository implements RankingRepository {

    private static final DateTimeFormatter KEY_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter KEY_HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final RedisTemplate<String, String> redisTemplate;

    public RedisProductRankingRepository(@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<RankingProductView> findDailyPage(LocalDate metricDate, int page, int size) {
        return findPage(buildDailyRankingKey(metricDate), page, size);
    }

    @Override
    public List<RankingProductView> findHourlyPage(LocalDateTime metricHour, int page, int size) {
        return findPage(buildHourlyRankingKey(metricHour), page, size);
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

    public String buildHourlyRankingKey(LocalDateTime metricHour) {
        return "ranking:hourly:" + metricHour.format(KEY_HOUR_FORMATTER);
    }

    private List<RankingProductView> findPage(String rankingKey, int page, int size) {
        long start = (long) (page - 1) * size;
        long end = start + size - 1L;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(rankingKey, start, end);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        AtomicLong rank = new AtomicLong(start + 1L);
        return tuples.stream()
                .map(tuple -> new RankingProductView(
                        UUID.fromString(tuple.getValue()),
                        rank.getAndIncrement(),
                        tuple.getScore()
                ))
                .toList();
    }
}
