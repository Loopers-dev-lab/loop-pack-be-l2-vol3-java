package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingKeyGenerator;
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

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;

@Repository
public class RankingRedisRepository implements RankingRepository {

    private static final Duration TTL = Duration.ofDays(2);

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
        String key = RankingKeyGenerator.dailyKey(date);
        redisTemplate.execute(
                zincrbyScript,
                List.of(key),
                String.valueOf(productDbId),
                String.valueOf(score),
                String.valueOf(TTL.toSeconds())
        );
    }

    public void incrementScoreSequential(LocalDate date, Long productDbId, double score) {
        String key = RankingKeyGenerator.dailyKey(date);
        Boolean hasKey = redisTemplate.hasKey(key);
        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(productDbId), score);
        if (Boolean.FALSE.equals(hasKey)) {
            redisTemplate.expire(key, TTL);
        }
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
        redisTemplate.expire(destKey, TTL);
        return count != null ? count : 0L;
    }
}
