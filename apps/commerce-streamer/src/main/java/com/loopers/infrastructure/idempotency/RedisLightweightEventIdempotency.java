package com.loopers.infrastructure.idempotency;

import com.loopers.infrastructure.config.CollectorIdempotencyProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 경량 이벤트 멱등: Redis SETNX만 사용하며 {@code event_handled} DB 폴백은 두지 않는다.
 * Redis 장애 시 예외는 상위 Kafka 리스너의 재시도·DLQ 정책으로 처리하고, 복구 후 DLQ 재처리로 맞춘다.
 */
@Component
public class RedisLightweightEventIdempotency implements LightweightEventIdempotency {

    private static final String KEY_PREFIX = "collector:idemp:light:";

    private final RedisTemplate<String, String> redisTemplate;
    private final CollectorIdempotencyProperties properties;

    public RedisLightweightEventIdempotency(
            RedisTemplate<String, String> redisTemplate,
            CollectorIdempotencyProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public boolean tryClaimFirstDelivery(String eventId) {
        String key = KEY_PREFIX + eventId;
        Duration ttl = Duration.ofDays(properties.redisTtlDays());
        Boolean created = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.TRUE.equals(created);
    }
}
