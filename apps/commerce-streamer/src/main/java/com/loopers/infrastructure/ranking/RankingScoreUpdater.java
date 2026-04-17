package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RankingKeyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;

@Component
public class RankingScoreUpdater {

    private static final Logger log = LoggerFactory.getLogger(RankingScoreUpdater.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final RankingWeightProperties weightProperties;

    public RankingScoreUpdater(
            @Qualifier(REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
            RankingWeightProperties weightProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.weightProperties = weightProperties;
    }

    public void incrementViewScore(Long productId) {
        incrementScore(productId, weightProperties.getView());
    }

    public void incrementLikeScore(Long productId) {
        incrementScore(productId, weightProperties.getLike());
    }

    public void incrementOrderScore(Long productId) {
        incrementScore(productId, weightProperties.getOrder());
    }

    private void incrementScore(Long productId, double score) {
        String key = RankingKeyBuilder.buildKey(LocalDate.now());
        String member = String.valueOf(productId);

        try {
            Boolean keyExists = redisTemplate.hasKey(key);
            redisTemplate.opsForZSet().incrementScore(key, member, score);

            if (Boolean.FALSE.equals(keyExists)) {
                redisTemplate.expire(key, RankingKeyBuilder.TTL_DAYS, TimeUnit.DAYS);
            }
        } catch (Exception e) {
            log.error("랭킹 점수 갱신 실패 - productId: {}, score: {}", productId, score, e);
        }
    }
}
