package com.loopers.application.collector;

import com.loopers.config.redis.RedisConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeRankingAggregationService {

    private final RankingKeyGenerator rankingKeyGenerator;

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${commerce.ranking.weight.view:0.1}")
    private double viewWeight;

    @Value("${commerce.ranking.weight.like:0.2}")
    private double likeWeight;

    @Value("${commerce.ranking.weight.order:0.6}")
    private double orderWeight;

    @Value("${commerce.ranking.weight.dwell:0.3}")
    private double dwellWeight;

    @Value("${commerce.ranking.dwell.min-seconds:5}")
    private int dwellMinSeconds;

    @Value("${commerce.ranking.dwell.max-seconds:600}")
    private int dwellMaxSeconds;

    @Value("${commerce.ranking.dwell.dedup-ttl-minutes:30}")
    private long dwellDedupTtlMinutes;

    @Value("${commerce.ranking.ttl-days:2}")
    private long ttlDays;

    public void applyView(Long productId, ZonedDateTime occurredAt) {
        addScoreToDailyAndHourly(productId, viewWeight, occurredAt);
    }

    public void applyLikeDelta(Long productId, long delta, ZonedDateTime occurredAt) {
        addScoreToDailyAndHourly(productId, likeWeight * delta, occurredAt);
    }

    public void applyDwell(Long productId, Long userId, int dwellTimeSeconds, ZonedDateTime occurredAt) {
        if (dwellTimeSeconds < dwellMinSeconds) {
            return;
        }
        int clamped = Math.min(dwellTimeSeconds, dwellMaxSeconds);

        if (userId != null && userId > 0L) {
            String dedupKey = "dwell:dedup:" + userId + ":" + productId;
            Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", Duration.ofMinutes(dwellDedupTtlMinutes));
            if (!Boolean.TRUE.equals(isNew)) {
                log.debug("Dwell dedup hit. userId={}, productId={}", userId, productId);
                return;
            }
        }

        double score = Math.log10(clamped) * dwellWeight;
        addScoreToDailyAndHourly(productId, score, occurredAt);
    }

    public void applyOrder(Long productId, long unitPrice, long quantity, ZonedDateTime occurredAt) {
        if (quantity <= 0L || unitPrice <= 0L) {
            return;
        }
        double rawScore = unitPrice * quantity;
        addScoreToDailyAndHourly(productId, orderWeight * rawScore, occurredAt);
    }

    private void addScoreToDailyAndHourly(Long productId, double score, ZonedDateTime occurredAt) {
        if (productId == null || productId <= 0L || score == 0D || occurredAt == null) {
            return;
        }

        String member = String.valueOf(productId);

        String dailyKey = rankingKeyGenerator.dailyKey(occurredAt);
        redisTemplate.opsForZSet().incrementScore(dailyKey, member, score);
        redisTemplate.expire(dailyKey, Duration.ofDays(ttlDays));

        String hourlyKey = rankingKeyGenerator.hourlyKey(occurredAt);
        redisTemplate.opsForZSet().incrementScore(hourlyKey, member, score);
        redisTemplate.expire(hourlyKey, Duration.ofDays(ttlDays));
    }
}
