package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RankingRedisUpdater {

    private static final String VIEW_PREFIX = "ranking:view:";
    private static final String LIKE_PREFIX = "ranking:like:";
    private static final String ORDER_PREFIX = "ranking:order:";
    private static final String ALL_PREFIX = "ranking:all:";
    private static final Duration TTL = Duration.ofDays(2);

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRedisUpdater(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void incrementViewCount(Long productId) {
        incrementRawCount(VIEW_PREFIX, productId, 1);
    }

    public void incrementLikeCount(Long productId, long delta) {
        incrementRawCount(LIKE_PREFIX, productId, delta);
    }

    public void incrementOrderCount(Long productId, int amount) {
        incrementRawCount(ORDER_PREFIX, productId, amount);
    }

    public void unionStoreRanking(LocalDate date) {
        String viewKey = keyOf(VIEW_PREFIX, date);
        String likeKey = keyOf(LIKE_PREFIX, date);
        String orderKey = keyOf(ORDER_PREFIX, date);
        String allKey = allKeyOf(date);

        redisTemplate.opsForZSet().unionAndStore(
                viewKey,
                List.of(likeKey, orderKey),
                allKey,
                Aggregate.SUM,
                Weights.of(1, 3, 10));
        redisTemplate.expire(allKey, TTL);

        Long size = redisTemplate.opsForZSet().zCard(allKey);
        log.info("[Ranking] ZUNIONSTORE 완료 — {} ({}건, WEIGHTS 1 3 10)", allKey, size);
    }

    public String allKeyOf(LocalDate date) {
        return keyOf(ALL_PREFIX, date);
    }

    private void incrementRawCount(String prefix, Long productId, double delta) {
        String key = keyOf(prefix, LocalDate.now());
        redisTemplate.opsForZSet()
                .incrementScore(key, String.valueOf(productId), delta);
        redisTemplate.expire(key, TTL);
    }

    private String keyOf(String prefix, LocalDate date) {
        return prefix + date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
