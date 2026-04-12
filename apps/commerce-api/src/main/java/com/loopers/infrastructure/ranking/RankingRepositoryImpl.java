package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
public class RankingRepositoryImpl implements RankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRepositoryImpl(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<RankedProduct> findTopN(String date, int offset, int size) {
        String key = KEY_PREFIX + date;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, offset, offset + size - 1);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        return tuples.stream()
                .map(t -> new RankedProduct(
                        Long.valueOf(t.getValue()),
                        t.getScore() != null ? t.getScore() : 0.0
                ))
                .toList();
    }

    @Override
    public Long findRank(String date, Long productId) {
        String key = KEY_PREFIX + date;
        return redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
    }

    @Override
    public long getTotalSize(String date) {
        String key = KEY_PREFIX + date;
        Long size = redisTemplate.opsForZSet().zCard(key);
        return size != null ? size : 0;
    }
}
