package com.loopers.infrastructure.ranking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.ranking.RankingDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCacheStore {

    private static final String RANKING_CACHE_KEY_PREFIX = "ranking:cache:";
    private static final Duration DAILY_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration WEEKLY_MONTHLY_CACHE_TTL = Duration.ofHours(1);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<List<RankingDto.RankingItemInfo>> getRankings(String period, String date, int page, int size) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey(period, date, page, size));
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, new TypeReference<>() {}));
        } catch (Exception e) {
            log.warn("랭킹 캐시 조회 실패 - period:{} date:{} page:{}", period, date, page, e);
            return Optional.empty();
        }
    }

    public void putRankings(String period, String date, int page, int size, List<RankingDto.RankingItemInfo> rankings) {
        try {
            String json = objectMapper.writeValueAsString(rankings);
            redisTemplate.opsForValue().set(cacheKey(period, date, page, size), json, ttlFor(period));
        } catch (Exception e) {
            log.warn("랭킹 캐시 저장 실패 - period:{} date:{} page:{}", period, date, page, e);
        }
    }

    private Duration ttlFor(String period) {
        return "daily".equals(period) ? DAILY_CACHE_TTL : WEEKLY_MONTHLY_CACHE_TTL;
    }

    private String cacheKey(String period, String date, int page, int size) {
        return RANKING_CACHE_KEY_PREFIX + "period=" + period + ":date=" + date + ":page=" + page + ":size=" + size;
    }
}
