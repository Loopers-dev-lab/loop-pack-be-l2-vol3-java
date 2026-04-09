package com.loopers.application.collector;

import com.loopers.config.redis.RedisConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCarryOverScheduler {

    private final RankingKeyGenerator rankingKeyGenerator;

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${commerce.ranking.ttl-days:2}")
    private long ttlDays;

    @Value("${commerce.ranking.carry-over.enabled:true}")
    private boolean carryOverEnabled;

    @Value("${commerce.ranking.carry-over.top-size:200}")
    private long carryOverTopSize;

    @Value("${commerce.ranking.carry-over.factor:0.3}")
    private double carryOverFactor;

    @Scheduled(
        cron = "${commerce.ranking.carry-over.cron:0 50 23 * * *}",
        zone = "${commerce.ranking.zone-id:Asia/Seoul}"
    )
    public void carryOverTopRanksToTomorrow() {
        if (!carryOverEnabled || carryOverTopSize <= 0L || carryOverFactor <= 0D) {
            return;
        }

        LocalDate today = rankingKeyGenerator.today();
        String sourceKey = rankingKeyGenerator.dailyKey(today);
        String targetKey = rankingKeyGenerator.dailyKey(today.plusDays(1));

        Set<ZSetOperations.TypedTuple<String>> topTuples = redisTemplate.opsForZSet()
            .reverseRangeWithScores(sourceKey, 0, carryOverTopSize - 1);

        if (topTuples == null || topTuples.isEmpty()) {
            return;
        }

        long copied = 0L;
        for (ZSetOperations.TypedTuple<String> tuple : topTuples) {
            if (tuple == null || tuple.getValue() == null || tuple.getScore() == null) {
                continue;
            }

            double carriedScore = tuple.getScore() * carryOverFactor;
            if (carriedScore <= 0D) {
                continue;
            }

            redisTemplate.opsForZSet().add(targetKey, tuple.getValue(), carriedScore);
            copied++;
        }

        redisTemplate.expire(targetKey, Duration.ofDays(ttlDays));
        log.info("Ranking carry-over completed. sourceKey={}, targetKey={}, copied={}", sourceKey, targetKey, copied);
    }
}
