package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.ProductRankingScoreModel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRankingScoreRedisRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private final RedisTemplate<String, String> redisTemplate;

    public ProductRankingScoreRedisRepository(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public List<ProductRankingScoreModel> findTopByDate(LocalDate date, long start, long end) {
        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(keyOf(date), start, end);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        return tuples.stream()
                .map(tuple -> ProductRankingScoreModel.create(
                        Long.parseLong(tuple.getValue()), date,
                        tuple.getScore() != null ? tuple.getScore() : 0))
                .toList();
    }

    public long countByDate(LocalDate date) {
        Long size = redisTemplate.opsForZSet().zCard(keyOf(date));
        return size != null ? size : 0;
    }

    public Optional<Long> findRankByProductId(Long productId, LocalDate date) {
        Long rank = redisTemplate.opsForZSet()
                .reverseRank(keyOf(date), String.valueOf(productId));
        return rank != null ? Optional.of(rank + 1) : Optional.empty();
    }

    private String keyOf(LocalDate date) {
        return KEY_PREFIX + date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
