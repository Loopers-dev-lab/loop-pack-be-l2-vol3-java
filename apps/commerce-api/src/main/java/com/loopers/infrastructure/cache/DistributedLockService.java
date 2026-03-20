package com.loopers.infrastructure.cache;

import com.loopers.config.redis.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Redis 기반 분산 락 서비스.
 * SETNX + TTL 기반으로 락을 구현.
 */
@Slf4j
@Service
public class DistributedLockService {

    private static final String LOCK_KEY_PREFIX = "lock:";

    private static final String UNLOCK_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> unlockScript;

    public DistributedLockService(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.unlockScript = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
    }

    /**
     * 락 획득 시도.
     *
     * @param key 락 키
     * @param ttl 락 TTL
     * @return 락 값 (획득 성공 시), 빈 Optional (획득 실패 시)
     */
    public Optional<String> tryLock(String key, Duration ttl) {
        try {
            String lockKey = buildLockKey(key);
            String lockValue = generateLockValue();

            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, ttl);

            if (Boolean.TRUE.equals(acquired)) {
                log.debug("락 획득 성공: key={}, value={}", key, lockValue);
                return Optional.of(lockValue);
            }

            log.debug("락 획득 실패: key={}", key);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("락 획득 중 오류 발생: key={}", key, e);
            return Optional.empty();
        }
    }

    /**
     * 락 해제.
     *
     * @param key 락 키
     * @param lockValue 락 획득 시 받은 값
     * @return 해제 성공 여부
     */
    public boolean unlock(String key, String lockValue) {
        try {
            String lockKey = buildLockKey(key);

            Long result = redisTemplate.execute(
                    unlockScript,
                    List.of(lockKey),
                    lockValue
            );

            boolean released = result != null && result == 1L;
            if (released) {
                log.debug("락 해제 성공: key={}", key);
            } else {
                log.debug("락 해제 실패 (소유권 불일치): key={}", key);
            }

            return released;
        } catch (Exception e) {
            log.warn("락 해제 중 오류 발생: key={}", key, e);
            return false;
        }
    }

    /**
     * 락을 획득하고 작업 실행 후 자동 해제.
     *
     * @param key 락 키
     * @param ttl 락 TTL
     * @param action 실행할 작업
     * @return 작업 결과 (락 획득 실패 시 빈 Optional)
     */
    public <T> Optional<T> executeWithLock(String key, Duration ttl, Supplier<T> action) {
        Optional<String> lockValue = tryLock(key, ttl);

        if (lockValue.isEmpty()) {
            return Optional.empty();
        }

        try {
            T result = action.get();
            return Optional.ofNullable(result);
        } finally {
            unlock(key, lockValue.get());
        }
    }

    /**
     * 락을 획득하고 작업 실행 후 자동 해제 (반환값 없음).
     *
     * @param key 락 키
     * @param ttl 락 TTL
     * @param action 실행할 작업
     * @return 락 획득 및 실행 성공 여부
     */
    public boolean executeWithLock(String key, Duration ttl, Runnable action) {
        Optional<String> lockValue = tryLock(key, ttl);

        if (lockValue.isEmpty()) {
            return false;
        }

        try {
            action.run();
            return true;
        } finally {
            unlock(key, lockValue.get());
        }
    }

    private String buildLockKey(String key) {
        return LOCK_KEY_PREFIX + key;
    }

    private String generateLockValue() {
        return UUID.randomUUID().toString();
    }
}
