package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.TokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
public class TokenRepositoryImpl implements TokenRepository {

    private static final String TOKEN_KEY_PREFIX = "queue:token:";

    private final RedisTemplate<String, String> redisTemplate;

    public TokenRepositoryImpl(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void saveToken(Long userId, String token, long ttlSeconds) {
        redisTemplate.opsForValue().set(
                TOKEN_KEY_PREFIX + userId, token, ttlSeconds, TimeUnit.SECONDS
        );
    }

    @Override
    public String getToken(Long userId) {
        return redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + userId);
    }

    @Override
    public void deleteToken(Long userId) {
        redisTemplate.delete(TOKEN_KEY_PREFIX + userId);
    }
}
