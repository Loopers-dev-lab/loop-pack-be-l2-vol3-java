package com.loopers.infrastructure.shared.entry;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.loopers.config.redis.RedisConfig;
import com.loopers.support.entry.EntryTokenStore;

@Component
public class RedisEntryTokenStore implements EntryTokenStore {

    private static final String TOKEN_KEY_PREFIX = "entry-token:";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisEntryTokenStore(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<String> getToken(Long userId) {
        String token = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + userId);
        return Optional.ofNullable(token);
    }
}
