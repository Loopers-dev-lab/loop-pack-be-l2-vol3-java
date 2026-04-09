package com.loopers.infrastructure.ranking.redis;

import com.loopers.application.ranking.ProductMetricsSummary;
import com.loopers.config.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class RedisProductRankingRepository {

    private static final DateTimeFormatter KEY_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter KEY_HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final Duration DAILY_RANKING_TTL = Duration.ofHours(48);
    private static final Duration HOURLY_RANKING_TTL = Duration.ofHours(2);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisProductRankingRepository(@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void replaceDailyRanking(LocalDate metricDate, Map<String, Double> rankingScores) {
        replaceRanking(buildDailyRankingKey(metricDate), DAILY_RANKING_TTL, rankingScores);
    }

    public void replaceHourlyRanking(LocalDateTime metricHour, Map<String, Double> rankingScores) {
        replaceRanking(buildHourlyRankingKey(metricHour), HOURLY_RANKING_TTL, rankingScores);
    }

    public void incrementDailyRanking(LocalDate metricDate, String productId, double scoreDelta) {
        incrementRanking(buildDailyRankingKey(metricDate), DAILY_RANKING_TTL, productId, scoreDelta);
    }

    public void incrementHourlyRanking(LocalDateTime metricHour, String productId, double scoreDelta) {
        incrementRanking(buildHourlyRankingKey(metricHour), HOURLY_RANKING_TTL, productId, scoreDelta);
    }

    public void carryOverDailyRanking(LocalDate sourceDate, LocalDate targetDate, double ratio) {
        carryOver(buildDailyRankingKey(sourceDate), buildDailyRankingKey(targetDate), DAILY_RANKING_TTL, ratio);
    }

    public void carryOverHourlyRanking(LocalDateTime sourceHour, LocalDateTime targetHour, double ratio) {
        carryOver(buildHourlyRankingKey(sourceHour), buildHourlyRankingKey(targetHour), HOURLY_RANKING_TTL, ratio);
    }

    public String buildDailyRankingKey(LocalDate metricDate) {
        return "ranking:all:" + metricDate.format(KEY_DATE_FORMATTER);
    }

    public String buildHourlyRankingKey(LocalDateTime metricHour) {
        return "ranking:hourly:" + metricHour.format(KEY_HOUR_FORMATTER);
    }

    public boolean hasDailyRanking(LocalDate metricDate) {
        return exists(buildDailyRankingKey(metricDate));
    }

    public boolean hasHourlyRanking(LocalDateTime metricHour) {
        return exists(buildHourlyRankingKey(metricHour));
    }

    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private void replaceRanking(String rankingKey, Duration ttl, Map<String, Double> rankingScores) {
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
        redisTemplate.expire(tempRankingKey, ttl);
        redisTemplate.rename(tempRankingKey, rankingKey);
        redisTemplate.expire(rankingKey, ttl);
    }

    private void incrementRanking(String rankingKey, Duration ttl, String productId, double scoreDelta) {
        redisTemplate.opsForZSet().incrementScore(rankingKey, productId, scoreDelta);
        redisTemplate.expire(rankingKey, ttl);
    }

    private void carryOver(String sourceKey, String targetKey, Duration ttl, double ratio) {
        if (exists(targetKey) || !exists(sourceKey)) {
            return;
        }

        Set<ZSetOperations.TypedTuple<String>> sourceTuples = redisTemplate.opsForZSet().reverseRangeWithScores(sourceKey, 0, -1);
        if (sourceTuples == null || sourceTuples.isEmpty()) {
            return;
        }

        Set<ZSetOperations.TypedTuple<String>> carriedTuples = sourceTuples.stream()
                .filter(tuple -> tuple.getValue() != null && tuple.getScore() != null)
                .map(tuple -> ZSetOperations.TypedTuple.of(tuple.getValue(), tuple.getScore() * ratio))
                .collect(Collectors.toSet());

        redisTemplate.opsForZSet().add(targetKey, carriedTuples);
        redisTemplate.expire(targetKey, ttl);
    }
}

