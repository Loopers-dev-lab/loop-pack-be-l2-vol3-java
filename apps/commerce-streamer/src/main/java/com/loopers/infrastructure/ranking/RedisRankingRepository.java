package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
@Repository
public class RedisRankingRepository implements RankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void incrementScore(Long productId, LocalDate date, double increment) {
        String key = KEY_PREFIX + date.format(DATE_FORMATTER);
        redisTemplate.opsForZSet().incrementScore(key, productId.toString(), increment);

        Instant expireAt = date.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant();
        redisTemplate.expireAt(key, expireAt);
    }
}
