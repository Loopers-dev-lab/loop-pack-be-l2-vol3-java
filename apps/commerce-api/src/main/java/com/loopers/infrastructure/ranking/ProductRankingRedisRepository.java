package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankingInfo;
import com.loopers.domain.ranking.ProductRankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
public class ProductRankingRedisRepository implements ProductRankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RedisTemplate<String, String> defaultRedisTemplate;
    private final RedisTemplate<String, String> masterRedisTemplate;

    public ProductRankingRedisRepository(
        RedisTemplate<String, String> defaultRedisTemplate,
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.defaultRedisTemplate = defaultRedisTemplate;
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Override
    public List<ProductRankingInfo> getRankings(String dateKey, int offset, int size) {
        String key = KEY_PREFIX + dateKey;
        Set<ZSetOperations.TypedTuple<String>> tuples =
            defaultRedisTemplate.opsForZSet().reverseRangeWithScores(key, offset, (long) offset + size - 1);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProductRankingInfo> rankings = new ArrayList<>();
        int index = 0;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            rankings.add(new ProductRankingInfo(
                Long.parseLong(tuple.getValue()),
                tuple.getScore(),
                (long) offset + index + 1
            ));
            index++;
        }
        return rankings;
    }

    @Override
    public Long getRank(String dateKey, Long productId) {
        String key = KEY_PREFIX + dateKey;
        Long rank = defaultRedisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
        return rank != null ? rank + 1 : null;
    }

    @Override
    public Double getScore(String dateKey, Long productId) {
        String key = KEY_PREFIX + dateKey;
        return defaultRedisTemplate.opsForZSet().score(key, String.valueOf(productId));
    }

    @Override
    public Long getTotalCount(String dateKey) {
        String key = KEY_PREFIX + dateKey;
        Long count = defaultRedisTemplate.opsForZSet().zCard(key);
        return count != null ? count : 0L;
    }

    @Override
    public void removeProduct(Long productId) {
        String member = String.valueOf(productId);
        String today = LocalDate.now().format(DATE_KEY_FORMATTER);
        String yesterday = LocalDate.now().minusDays(1).format(DATE_KEY_FORMATTER);

        masterRedisTemplate.opsForZSet().remove(KEY_PREFIX + today, member);
        masterRedisTemplate.opsForZSet().remove(KEY_PREFIX + yesterday, member);
    }
}
