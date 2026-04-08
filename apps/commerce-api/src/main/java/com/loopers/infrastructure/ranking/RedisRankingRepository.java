package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Repository
public class RedisRankingRepository implements RankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public List<Long> findProductIdsByRank(LocalDate date, long offset, long limit) {
        String key = KEY_PREFIX + date.format(DATE_FORMATTER);
        Set<String> members = redisTemplate.opsForZSet().reverseRange(key, offset, offset + limit - 1);
        if (members == null) return List.of();
        return members.stream().map(Long::parseLong).toList();
    }

    @Override
    public long countByDate(LocalDate date) {
        String key = KEY_PREFIX + date.format(DATE_FORMATTER);
        Long count = redisTemplate.opsForZSet().size(key);
        return count != null ? count : 0L;
    }
}
