package com.loopers.domain.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.event.ranking.RankingKeyGenerator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;

@Service
public class RankingScoreUpdater {
    private final RedisTemplate<String, String> masterRedisTemplate;
    private final Clock clock;
    private final double viewWeight;
    private final double likeWeight;
    private final double orderWeight;

    private static final Duration KEY_TTL = Duration.ofDays(2);

    public RankingScoreUpdater(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
            Clock clock,
            @Value("${ranking.weight.view:0.1}") double viewWeight,
            @Value("${ranking.weight.like:0.2}") double likeWeight,
            @Value("${ranking.weight.order:0.7}") double orderWeight
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.clock = clock;
        this.viewWeight = viewWeight;
        this.likeWeight = likeWeight;
        this.orderWeight = orderWeight;
    }

    public void incrementView(Long productId) {
        incrementScore(productId, viewWeight);
    }

    public void incrementLike(Long productId) {
        incrementScore(productId, likeWeight);
    }

    public void decrementLike(Long productId) {
        incrementScore(productId, -likeWeight);
    }

    public void incrementOrder(Long productId, long price, int quantity) {
        double score = orderWeight * price * quantity;
        incrementScore(productId, score);
    }

    private void incrementScore(Long productId, double score) {
        String key = RankingKeyGenerator.keyOf(LocalDate.now(clock));
        String member = String.valueOf(productId);
        masterRedisTemplate.opsForZSet().incrementScore(key, member, score);
        masterRedisTemplate.expire(key, KEY_TTL);
    }
}
