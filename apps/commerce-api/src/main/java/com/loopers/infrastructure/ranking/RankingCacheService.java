package com.loopers.infrastructure.ranking;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class RankingCacheService {

    static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RedisTemplate<String, String> defaultRedisTemplate;

    public RankingCacheService(RedisTemplate<String, String> defaultRedisTemplate) {
        this.defaultRedisTemplate = defaultRedisTemplate;
    }

    public Long getRank(String date, Long productId) {
        String key = KEY_PREFIX + date;
        Long rank = defaultRedisTemplate.opsForZSet().reverseRank(key, productId.toString());
        return rank == null ? null : rank + 1;
    }

    public Long getTodayRank(Long productId) {
        String today = LocalDate.now().format(DATE_FORMATTER);
        return getRank(today, productId);
    }

    public List<String> getProductIds(String date, int offset, int size) {
        String key = KEY_PREFIX + date;
        Set<String> members = defaultRedisTemplate.opsForZSet()
            .reverseRange(key, offset, (long) offset + size - 1);
        return members == null ? List.of() : new ArrayList<>(members);
    }

    public long getTotalCount(String date) {
        String key = KEY_PREFIX + date;
        Long count = defaultRedisTemplate.opsForZSet().zCard(key);
        return count == null ? 0L : count;
    }
}
