package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueConstants;
import com.loopers.domain.queue.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
public class TokenRepositoryImpl implements TokenRepository {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(String userId, String token, long ttlSeconds) {
        redisTemplate.opsForValue().set(QueueConstants.TOKEN_KEY_PREFIX + userId, token, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public Optional<String> findToken(String userId) {
        String token = redisTemplate.opsForValue().get(QueueConstants.TOKEN_KEY_PREFIX + userId);
        return Optional.ofNullable(token);
    }

    @Override
    public void delete(String userId) {
        redisTemplate.delete(QueueConstants.TOKEN_KEY_PREFIX + userId);
    }
}