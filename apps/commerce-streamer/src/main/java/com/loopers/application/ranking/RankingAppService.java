package com.loopers.application.ranking;

import com.loopers.config.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class RankingAppService {

    private static final String RAW_KEY_PREFIX = "ranking:raw:";
    private static final String HOURLY_KEY_PREFIX = "ranking:hourly:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final long TTL_DAYS = 2;
    private static final Duration HOURLY_TTL = Duration.ofHours(2);

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.6;

    private final StringRedisTemplate redisTemplate;

    public RankingAppService(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    public void updateViewRanking(Long productId) {
        trackRawCount(productId, "v");
        increaseHourlyScore(productId, VIEW_WEIGHT);
    }

    public void updateLikeRanking(Long productId) {
        trackRawCount(productId, "l");
        increaseHourlyScore(productId, LIKE_WEIGHT);
    }

    public void updateOrderRanking(List<Long> productIds, long totalAmount) {
        long amountPerProduct = productIds.isEmpty() ? 0 : totalAmount / productIds.size();
        for (Long productId : productIds) {
            trackRawAmount(productId, "o", Math.max(amountPerProduct, 1));
            increaseHourlyScore(productId, ORDER_WEIGHT);
        }
    }

    private void trackRawCount(Long productId, String metricType) {
        trackRawAmount(productId, metricType, 1);
    }

    private void trackRawAmount(Long productId, String metricType, long amount) {
        String date = LocalDate.now().format(DATE_FORMAT);
        String rawKey = RAW_KEY_PREFIX + date;
        String pid = String.valueOf(productId);

        redisTemplate.opsForHash().increment(rawKey, pid + ":" + metricType, amount);
        redisTemplate.opsForHash().put(rawKey, pid + ":t", String.valueOf(Instant.now().getEpochSecond()));
        redisTemplate.expire(rawKey, Duration.ofDays(TTL_DAYS));
    }

    private void increaseHourlyScore(Long productId, double delta) {
        String hourlyKey = HOURLY_KEY_PREFIX + LocalDateTime.now().format(HOUR_FORMAT);
        redisTemplate.opsForZSet().incrementScore(hourlyKey, String.valueOf(productId), delta);
        redisTemplate.expire(hourlyKey, HOURLY_TTL);
    }
}
