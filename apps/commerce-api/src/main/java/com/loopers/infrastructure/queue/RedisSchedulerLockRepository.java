package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.SchedulerLockRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

/**
 * {@link SchedulerLockRepository}의 Redis 구현.
 * <p>
 * 락: {@code SET key value NX EX ttl}에 해당하는
 * {@link org.springframework.data.redis.core.ValueOperations#setIfAbsent}
 * 로 키가 없을 때만 설정해 경량(단일키·TTL)으로 분산 락을 구현.
 * 하트비트: 일반 {@code SET} + TTL로 마지막 성공 틱 시각 등을 덮어쓴다.
 */
@Repository
public class RedisSchedulerLockRepository implements SchedulerLockRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisSchedulerLockRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 키가 존재하지 않을 때만 값을 넣고 true. 이미 있으면 false (다른 인스턴스가 락 보유 중).
     */
    @Override
    public boolean tryAcquireLock(String lockKey, String lockValue, long ttlSeconds) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 하트비트 키를 갱신한다. TTL마다 만료되므로 스케줄러가 멈추면 키가 사라져 장애 감지에 쓸 수 있다.
     */
    @Override
    public void updateHeartbeat(String heartbeatKey, String value, long ttlSeconds) {
        redisTemplate.opsForValue().set(heartbeatKey, value, ttlSeconds, TimeUnit.SECONDS);
    }
}
