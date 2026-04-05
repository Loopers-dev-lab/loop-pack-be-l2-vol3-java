package com.loopers.infrastructure.shared.entry;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.loopers.config.redis.RedisConfig;
import com.loopers.support.entry.EntryTokenStore;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * Redis 기반 {@link EntryTokenStore} 구현체.
 *
 * <p>{@code entry-token:{userId}} 키로 토큰을 관리한다.</p>
 */
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

    @Override
    public void validate(Long userId, String token) {
        String storedToken = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + userId);
        if (storedToken == null || !storedToken.equals(token)) {
            throw new CoreException(ErrorType.INVALID_ENTRY_TOKEN);
        }
    }

    @Override
    public void delete(Long userId) {
        redisTemplate.delete(TOKEN_KEY_PREFIX + userId);
    }
}
