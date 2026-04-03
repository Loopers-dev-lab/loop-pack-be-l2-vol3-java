package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.QueueTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class QueueTokenRepositoryImpl implements QueueTokenRepository {

    private static final String KEY_PREFIX = "queue:";
    private static final String KEY_INFIX = ":token:";

    private final RedisTemplate<String, String> redisTemplate;

    public QueueTokenRepositoryImpl(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void issueToken(String eventId, Long userId, String token, long ttlSeconds) {
        String key = generateKey(eventId, userId);
        redisTemplate.opsForValue().set(key, token, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public Optional<String> getToken(String eventId, Long userId) {
        String key = generateKey(eventId, userId);
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    @Override
    public long getTokenTtl(String eventId, Long userId) {
        String key = generateKey(eventId, userId);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : -2L;
    }

    @Override
    public void removeToken(String eventId, Long userId) {
        String key = generateKey(eventId, userId);
        redisTemplate.delete(key);
    }

    private String generateKey(String eventId, Long userId) {
        return KEY_PREFIX + eventId + KEY_INFIX + userId;
    }
}
