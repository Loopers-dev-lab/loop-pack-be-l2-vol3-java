package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.SchedulerLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Redis SETNX 기반 스케줄러 리더 선출 락 구현.
 *
 * <p>owner 검증 Lua unlock으로 다른 인스턴스의 락을 삭제하지 않는다.
 * Redis 장애 시 false를 반환하여 이번 주기를 skip한다.</p>
 */
@Slf4j
@Component
public class RedisSchedulerLock implements SchedulerLock {

    private final RedisTemplate<String, String> redisTemplateMaster;

    private static final String LOCK_KEY = "queue:scheduler:lock";
    private static final Duration LOCK_TTL = Duration.ofMillis(1000);
    private final String instanceId = UUID.randomUUID().toString();

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        redis.call('del', KEYS[1])
                        return 1
                    end
                    return 0
                    """,
                    Long.class
            );

    public RedisSchedulerLock(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplateMaster) {
        this.redisTemplateMaster = redisTemplateMaster;
    }

    @Override
    public boolean tryAcquire() {
        try {
            Boolean acquired = redisTemplateMaster.opsForValue()
                    .setIfAbsent(LOCK_KEY, instanceId, LOCK_TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("스케줄러 리더 선출 실패 (Redis 장애), 이번 주기 skip: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void release() {
        try {
            redisTemplateMaster.execute(
                    UNLOCK_SCRIPT,
                    List.of(LOCK_KEY),
                    instanceId
            );
        } catch (Exception e) {
            log.warn("락 해제 실패 (TTL 자연 만료 대기): {}", e.getMessage());
        }
    }
}
