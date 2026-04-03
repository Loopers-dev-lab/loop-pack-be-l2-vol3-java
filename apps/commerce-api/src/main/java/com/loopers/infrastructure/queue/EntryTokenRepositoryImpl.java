package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/**
 * 입장 토큰 Redis 구현체.
 *
 * <p>기존 {@code PaymentLockService}와 동일한 패턴:
 * {@code @Qualifier("redisTemplateMaster")} Master 전용 + Lua script 원자적 실행.</p>
 *
 * <p>Redis 키: {@code order:entry-token:{userId}} (String, TTL 300초)</p>
 */
@Slf4j
@Repository
public class EntryTokenRepositoryImpl implements EntryTokenRepository {

    private final RedisTemplate<String, String> redisTemplateMaster;

    private static final String KEY_PREFIX = "order:entry-token:";

    /**
     * Lua script: GET → 값 비교 → DEL (원자적 실행, 1회 사용 보장).
     */
    private static final DefaultRedisScript<Long> VALIDATE_AND_DELETE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local v = redis.call('get', KEYS[1])
                    if v == ARGV[1] then
                        redis.call('del', KEYS[1])
                        return 1
                    end
                    return 0
                    """,
                    Long.class
            );

    /**
     * Lua script: GET → 값 비교 (삭제 없음). preHandle 검증 전용.
     */
    private static final DefaultRedisScript<Long> VALIDATE_ONLY_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local v = redis.call('get', KEYS[1])
                    if v == ARGV[1] then
                        return 1
                    end
                    return 0
                    """,
                    Long.class
            );

    private final RedisTemplate<String, String> redisTemplateReadOnly;

    public EntryTokenRepositoryImpl(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplateMaster,
            RedisTemplate<String, String> redisTemplateReadOnly) {
        this.redisTemplateMaster = redisTemplateMaster;
        this.redisTemplateReadOnly = redisTemplateReadOnly;
    }

    @Override
    public boolean setIfAbsent(Long userId, String token, Duration ttl) {
        Boolean result = redisTemplateMaster.opsForValue()
                .setIfAbsent(KEY_PREFIX + userId, token, ttl);
        return Boolean.TRUE.equals(result);
    }

    /**
     * Replica 우선 읽기. 순번 조회(getPosition)에서 토큰 존재 확인용.
     */
    @Override
    public String get(Long userId) {
        return redisTemplateReadOnly.opsForValue().get(KEY_PREFIX + userId);
    }

    @Override
    public boolean delete(Long userId) {
        Boolean result = redisTemplateMaster.delete(KEY_PREFIX + userId);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean validateAndDelete(Long userId, String token) {
        Long result = redisTemplateMaster.execute(
                VALIDATE_AND_DELETE_SCRIPT,
                List.of(KEY_PREFIX + userId),
                token
        );
        return result != null && result == 1L;
    }

    @Override
    public boolean validate(Long userId, String token) {
        Long result = redisTemplateMaster.execute(
                VALIDATE_ONLY_SCRIPT,
                List.of(KEY_PREFIX + userId),
                token
        );
        return result != null && result == 1L;
    }
}
