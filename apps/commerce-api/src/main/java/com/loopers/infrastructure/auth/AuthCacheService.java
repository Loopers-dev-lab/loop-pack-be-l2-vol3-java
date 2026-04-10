package com.loopers.infrastructure.auth;

import com.loopers.config.redis.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Component
public class AuthCacheService {

    private static final String AUTH_CACHE_KEY_PREFIX = "auth-cache:";
    private static final Duration AUTH_CACHE_TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, String> defaultRedisTemplate;
    private final RedisTemplate<String, String> masterRedisTemplate;

    public AuthCacheService(
        RedisTemplate<String, String> defaultRedisTemplate,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.defaultRedisTemplate = defaultRedisTemplate;
        this.masterRedisTemplate = masterRedisTemplate;
    }

    public Optional<Long> getCachedUserId(String loginId, String rawPassword) {
        String key = authKey(loginId, rawPassword);
        String value = defaultRedisTemplate.opsForValue().get(key);
        return Optional.ofNullable(value).map(Long::parseLong);
    }

    public void cache(String loginId, String rawPassword, Long userId) {
        String key = authKey(loginId, rawPassword);
        masterRedisTemplate.opsForValue().set(key, String.valueOf(userId), AUTH_CACHE_TTL);
    }

    private String authKey(String loginId, String rawPassword) {
        return AUTH_CACHE_KEY_PREFIX + sha256(loginId + ":" + rawPassword);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
