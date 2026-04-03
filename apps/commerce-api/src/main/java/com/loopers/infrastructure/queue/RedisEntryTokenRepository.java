package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RedisEntryTokenRepository implements EntryTokenRepository {

    private static final String TOKEN_KEY_PREFIX = "entry-token:";

    private final RedisTemplate<String, String> redisTemplateMaster;

    @Override
    public boolean issueIfAbsent(Long userId, String token, long ttlSeconds) {
        // SET NX EX — 이미 토큰이 있으면 덮어쓰지 않음 (멱등성 보장)
        // true: 새로 발급, false: 이미 존재
        String key = TOKEN_KEY_PREFIX + userId;
        return Boolean.TRUE.equals(
                redisTemplateMaster.opsForValue().setIfAbsent(key, token, ttlSeconds, TimeUnit.SECONDS)
        );
    }

    @Override
    public boolean existsByUserId(Long userId) {
        return Boolean.TRUE.equals(
                redisTemplateMaster.hasKey(TOKEN_KEY_PREFIX + userId)
        );
    }

    @Override
    public void delete(Long userId) {
        redisTemplateMaster.delete(TOKEN_KEY_PREFIX + userId);
    }
}
