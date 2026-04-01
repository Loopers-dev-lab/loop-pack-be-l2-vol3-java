package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;

@Repository
public class RedisEntryTokenRepository implements EntryTokenRepository {

    private static final String TOKEN_KEY_PREFIX = "entry-token:";

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisTemplate<String, String> defaultRedisTemplate;

    public RedisEntryTokenRepository(
            @Qualifier(REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
            RedisTemplate<String, String> defaultRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.defaultRedisTemplate = defaultRedisTemplate;
    }

    @Override
    public void issueToken(Long userId, String token, Duration ttl) {
        masterRedisTemplate.opsForValue()
                .set(TOKEN_KEY_PREFIX + userId, token, ttl);
    }

    @Override
    public Optional<String> getToken(Long userId) {
        String token = defaultRedisTemplate.opsForValue()
                .get(TOKEN_KEY_PREFIX + userId);
        return Optional.ofNullable(token);
    }

    @Override
    public void deleteToken(Long userId) {
        masterRedisTemplate.delete(TOKEN_KEY_PREFIX + userId);
    }

    @Override
    public boolean validateToken(Long userId, String token) {
        return getToken(userId)
                .map(stored -> stored.equals(token))
                .orElse(false);
    }
}
