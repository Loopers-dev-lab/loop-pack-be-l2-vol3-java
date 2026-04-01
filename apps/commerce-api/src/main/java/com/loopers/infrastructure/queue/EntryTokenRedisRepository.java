package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;

@Repository
public class EntryTokenRedisRepository implements EntryTokenRepository {

    private static final String KEY_PREFIX = "queue:token:";

    private final RedisTemplate<String, String> redisTemplate;

    public EntryTokenRedisRepository(
            @Qualifier(REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void issue(Long memberId, String token, int ttlSeconds) {
        String key = KEY_PREFIX + memberId;
        redisTemplate.opsForValue().set(key, token, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public Optional<String> findToken(Long memberId) {
        String key = KEY_PREFIX + memberId;
        String token = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(token);
    }

    @Override
    public void consume(Long memberId) {
        String key = KEY_PREFIX + memberId;
        redisTemplate.delete(key);
        redisTemplate.delete(ISSUED_AT_PREFIX + memberId);
    }

    @Override
    public void recordIssuedAt(Long memberId, int ttlSeconds) {
        String key = ISSUED_AT_PREFIX + memberId;
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public Optional<Long> findIssuedAt(Long memberId) {
        String key = ISSUED_AT_PREFIX + memberId;
        String value = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(value).map(Long::parseLong);
    }

    private static final String ISSUED_AT_PREFIX = "queue:token-issued-at:";
}
