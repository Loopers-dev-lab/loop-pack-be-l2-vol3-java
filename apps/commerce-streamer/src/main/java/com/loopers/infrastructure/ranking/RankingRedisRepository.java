package com.loopers.infrastructure.ranking;

import com.loopers.ranking.RankingKeyGenerator;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;

@Repository
public class RankingRedisRepository implements RankingRepository {

    private static final Duration DAILY_TTL = Duration.ofDays(2);
    private static final Duration HOURLY_TTL = Duration.ofHours(2);

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Double> zincrbyScript;

    public RankingRedisRepository(
            @Qualifier(REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.zincrbyScript = new DefaultRedisScript<>();
        this.zincrbyScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("lua/ranking-zincrby.lua"))
        );
        this.zincrbyScript.setResultType(Double.class);
    }

    @Override
    public void incrementScore(LocalDate date, Long productDbId, double score) {
        String dailyKey = RankingKeyGenerator.dailyKey(date);
        int hour = java.time.LocalTime.now().getHour();
        String hourlyKey = RankingKeyGenerator.hourlyKey(date, hour);
        redisTemplate.execute(
                zincrbyScript,
                List.of(dailyKey, hourlyKey),
                String.valueOf(productDbId),
                String.valueOf(score),
                String.valueOf(DAILY_TTL.toSeconds()),
                String.valueOf(HOURLY_TTL.toSeconds())
        );
    }

    public void incrementScoreSequential(LocalDate date, Long productDbId, double score) {
        String key = RankingKeyGenerator.dailyKey(date);
        Boolean hasKey = redisTemplate.hasKey(key);
        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(productDbId), score);
        if (Boolean.FALSE.equals(hasKey)) {
            redisTemplate.expire(key, DAILY_TTL);
        }
    }

    @Override
    public void addAllToShadow(LocalDate date, Map<Long, Double> productScores) {
        String shadowKey = RankingKeyGenerator.shadowKey(date);
        redisTemplate.delete(shadowKey);
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
                new java.util.HashSet<>();
        for (Map.Entry<Long, Double> entry : productScores.entrySet()) {
            tuples.add(new org.springframework.data.redis.core.DefaultTypedTuple<>(
                    String.valueOf(entry.getKey()), entry.getValue()));
        }
        if (!tuples.isEmpty()) {
            redisTemplate.opsForZSet().add(shadowKey, tuples);
            redisTemplate.expire(shadowKey, DAILY_TTL);
        }
    }

    @Override
    public void renameShadowToMain(LocalDate date) {
        String shadowKey = RankingKeyGenerator.shadowKey(date);
        String mainKey = RankingKeyGenerator.dailyKey(date);
        redisTemplate.rename(shadowKey, mainKey);
        redisTemplate.expire(mainKey, DAILY_TTL);
    }

    @Override
    public long carryOver(LocalDate sourceDate, LocalDate destDate, double weight) {
        String sourceKey = RankingKeyGenerator.dailyKey(sourceDate);
        String destKey = RankingKeyGenerator.dailyKey(destDate);

        Boolean sourceExists = redisTemplate.hasKey(sourceKey);
        if (Boolean.FALSE.equals(sourceExists)) {
            return 0L;
        }

        Long count = redisTemplate.opsForZSet().unionAndStore(
                sourceKey,
                Collections.emptyList(),
                destKey,
                Aggregate.SUM,
                Weights.of(weight)
        );
        redisTemplate.expire(destKey, DAILY_TTL);
        return count != null ? count : 0L;
    }

    @Override
    public long carryOverHourly(LocalDate date, int sourceHour, int destHour, double weight) {
        String sourceKey = RankingKeyGenerator.hourlyKey(date, sourceHour);
        String destKey = RankingKeyGenerator.hourlyKey(date, destHour);

        Boolean sourceExists = redisTemplate.hasKey(sourceKey);
        if (Boolean.FALSE.equals(sourceExists)) {
            return 0L;
        }

        Long count = redisTemplate.opsForZSet().unionAndStore(
                sourceKey,
                Collections.emptyList(),
                destKey,
                Aggregate.SUM,
                Weights.of(weight)
        );
        redisTemplate.expire(destKey, HOURLY_TTL);
        return count != null ? count : 0L;
    }
}
