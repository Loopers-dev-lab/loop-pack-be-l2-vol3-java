package com.loopers.infrastructure.waitingroom;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.waitingroom.EntryGate;
import com.loopers.domain.waitingroom.WaitingRoomErrorCode;
import com.loopers.infrastructure.waitingroom.metrics.WaitingRoomMetricNames;
import com.loopers.support.error.CoreException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class EntryGateRedis implements EntryGate {

    private static final String TOKEN_KEY_PREFIX = "queue:entry-token:";
    private static final String ACTIVE_SET_KEY = "queue:entry-gate-members";

    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry registry;
    private final long tokenTtlSeconds;

    public EntryGateRedis(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
            MeterRegistry registry,
            @Value("${queue.token.ttl-seconds:300}") long tokenTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.registry = registry;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    @Override
    public String issueToken(Long userId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                TOKEN_KEY_PREFIX + userId, token, tokenTtlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForZSet().add(
                ACTIVE_SET_KEY, String.valueOf(userId),
                System.currentTimeMillis() + tokenTtlSeconds * 1000);
        return token;
    }

    @Override
    public String getToken(Long userId) {
        return redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + userId);
    }

    @Override
    public void validateToken(Long userId, String token) {
        String stored = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + userId);
        if (stored == null || !stored.equals(token)) {
            throw new CoreException(WaitingRoomErrorCode.INVALID_TOKEN);
        }
    }

    @Override
    public void completeEntry(Long userId) {
        redisTemplate.delete(TOKEN_KEY_PREFIX + userId);
        redisTemplate.opsForZSet().remove(ACTIVE_SET_KEY, String.valueOf(userId));
    }

    @Override
    public long getActiveCount() {
        Long expired = redisTemplate.opsForZSet()
                .removeRangeByScore(ACTIVE_SET_KEY, 0, System.currentTimeMillis());
        if (expired != null && expired > 0) {
            registry.counter(WaitingRoomMetricNames.TOKEN_EXPIRED_TOTAL).increment(expired);
        }
        Long count = redisTemplate.opsForZSet().zCard(ACTIVE_SET_KEY);
        return count != null ? count : 0;
    }
}
