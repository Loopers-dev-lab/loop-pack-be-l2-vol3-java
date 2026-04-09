package com.loopers.infrastructure.ranking.redis;

import com.loopers.config.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class RedisProductRankingRepository {

    private static final DateTimeFormatter KEY_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Duration DAILY_RANKING_TTL = Duration.ofHours(48);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisProductRankingRepository(@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void replaceDailyRanking(LocalDate metricDate, Map<String, Double> rankingScores) {
        String rankingKey = buildDailyRankingKey(metricDate);
        String tempRankingKey = rankingKey + ":sync";

        redisTemplate.delete(tempRankingKey);
        if (rankingScores.isEmpty()) {
            redisTemplate.delete(rankingKey);
            return;
        }

        Set<ZSetOperations.TypedTuple<String>> tuples = rankingScores.entrySet().stream()
                .map(entry -> ZSetOperations.TypedTuple.of(entry.getKey(), entry.getValue()))
                .collect(Collectors.toSet());

        redisTemplate.opsForZSet().add(tempRankingKey, tuples);
        redisTemplate.expire(tempRankingKey, DAILY_RANKING_TTL);
        redisTemplate.rename(tempRankingKey, rankingKey);
        redisTemplate.expire(rankingKey, DAILY_RANKING_TTL);
    }

    public String buildDailyRankingKey(LocalDate metricDate) {
        return "ranking:all:" + metricDate.format(KEY_DATE_FORMATTER);
    }
}
