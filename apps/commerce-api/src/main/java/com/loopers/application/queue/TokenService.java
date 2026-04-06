package com.loopers.application.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(QueueProperties.class)
public class TokenService {

    private final RedisTemplate<String, String> redisTemplate;
    private final QueueProperties queueProperties;

    public String issueToken(Long userId) {
        String token = UUID.randomUUID().toString();
        String key = getTokenKey(userId);
        redisTemplate.opsForValue().set(key, token, queueProperties.tokenTtlSeconds(), TimeUnit.SECONDS);
        log.info("Token issued for userId={}, token={}", userId, token);
        return token;
    }

    public boolean validateToken(Long userId, String token) {
        if (token == null) {
            return false;
        }
        String key = getTokenKey(userId);
        String stored = redisTemplate.opsForValue().get(key);
        return token.equals(stored);
    }

    public void deleteToken(Long userId) {
        String key = getTokenKey(userId);
        redisTemplate.delete(key);
        log.info("Token deleted for userId={}", userId);
    }

    public boolean hasToken(Long userId) {
        String key = getTokenKey(userId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public Optional<String> getToken(Long userId) {
        String key = getTokenKey(userId);
        String token = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(token);
    }

    private String getTokenKey(Long userId) {
        return queueProperties.tokenPrefix() + ":" + userId;
    }
}