package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Repository
@Slf4j
public class RankingRepositoryImpl implements RankingRepository {

    private static final String RANKING_KEY_PREFIX = "ranking:all:";
    private static final String HOURLY_KEY_PREFIX = "ranking:hourly:";
    private static final String LIKED_KEY_PREFIX = "ranking:liked:";
    private static final Duration TTL = Duration.ofDays(2);
    private static final Duration HOURLY_TTL = Duration.ofSeconds(10800);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final RedisTemplate<String, String> redisTemplateMaster;

    public RankingRepositoryImpl(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplateMaster) {
        this.redisTemplateMaster = redisTemplateMaster;
    }

    @Override
    public void incrementScore(Long productId, double score, LocalDate date) {
        String key = RANKING_KEY_PREFIX + date.format(DATE_FORMAT);
        String member = String.valueOf(productId);

        redisTemplateMaster.opsForZSet().incrementScore(key, member, score);
        redisTemplateMaster.expire(key, TTL);
    }

    @Override
    public boolean addLikeIfAbsent(Long productId, Long userId, LocalDate date) {
        String key = LIKED_KEY_PREFIX + date.format(DATE_FORMAT);
        String member = productId + ":" + userId;

        Long result = redisTemplateMaster.opsForSet().add(key, member);
        redisTemplateMaster.expire(key, TTL);
        return result != null && result > 0;
    }

    @Override
    public boolean removeLikeIfPresent(Long productId, Long userId, LocalDate date) {
        String key = LIKED_KEY_PREFIX + date.format(DATE_FORMAT);
        String member = productId + ":" + userId;

        Long result = redisTemplateMaster.opsForSet().remove(key, member);
        return result != null && result > 0;
    }

    @Override
    public void incrementScoreBatch(Map<Long, Double> productScores, LocalDate date) {
        if (productScores.isEmpty()) return;

        String key = RANKING_KEY_PREFIX + date.format(DATE_FORMAT);

        redisTemplateMaster.executePipelined((RedisCallback<Object>) connection -> {
            byte[] keyBytes = key.getBytes();
            productScores.forEach((productId, score) ->
                connection.zSetCommands().zIncrBy(keyBytes, score,
                    String.valueOf(productId).getBytes()));
            return null;
        });

        redisTemplateMaster.expire(key, TTL);
    }

    @Override
    public void incrementHourlyScore(Long productId, double score, LocalDateTime occurredAt) {
        LocalDateTime time = occurredAt != null ? occurredAt : LocalDateTime.now();
        String key = HOURLY_KEY_PREFIX + time.format(HOUR_FORMAT);
        String member = String.valueOf(productId);

        redisTemplateMaster.opsForZSet().incrementScore(key, member, score);
        redisTemplateMaster.expire(key, HOURLY_TTL);
    }

    private static final DefaultRedisScript<Long> CARRY_OVER_SCRIPT;
    static {
        CARRY_OVER_SCRIPT = new DefaultRedisScript<>();
        CARRY_OVER_SCRIPT.setScriptText("""
            local exists = redis.call('EXISTS', KEYS[2])
            if exists == 0 then
                return 0
            end
            redis.call('ZUNIONSTORE', KEYS[1], 1, KEYS[2], 'WEIGHTS', ARGV[1])
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """);
        CARRY_OVER_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean carryOver(LocalDate from, LocalDate to, double weight) {
        String fromKey = RANKING_KEY_PREFIX + from.format(DATE_FORMAT);
        String toKey = RANKING_KEY_PREFIX + to.format(DATE_FORMAT);

        Long result = redisTemplateMaster.execute(
                CARRY_OVER_SCRIPT,
                List.of(toKey, fromKey),
                String.valueOf(weight),
                String.valueOf(TTL.getSeconds())
        );
        return result != null && result == 1;
    }
}
