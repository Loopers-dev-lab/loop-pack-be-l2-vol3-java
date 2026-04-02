package com.loopers.infrastructure.queue;

import com.loopers.application.queue.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisTokenService implements TokenService {

    private static final String TOKEN_KEY_PREFIX = "entry-token:";

    @Value("${queue.token.ttl-seconds:300}")
    private long tokenTtlSeconds;

    private final StringRedisTemplate redisTemplateMaster;

    @Override
    public void issue(Long userId) {
        redisTemplateMaster.opsForValue().set(TOKEN_KEY_PREFIX + userId, "1", Duration.ofSeconds(tokenTtlSeconds));
    }

    @Override
    public boolean validate(Long userId) {
        return Boolean.TRUE.equals(redisTemplateMaster.hasKey(TOKEN_KEY_PREFIX + userId));
    }

    @Override
    public void delete(Long userId) {
        redisTemplateMaster.delete(TOKEN_KEY_PREFIX + userId);
    }
}
