package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.SchedulerHeartbeatRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;

@Repository
public class SchedulerHeartbeatRedisRepository implements SchedulerHeartbeatRepository {

    private static final String KEY_PREFIX = "queue:heartbeat:";

    private final RedisTemplate<String, String> redisTemplate;

    public SchedulerHeartbeatRedisRepository(
            @Qualifier(REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void recordHeartbeat(String schedulerName, int ttlSeconds) {
        String key = KEY_PREFIX + schedulerName;
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public boolean isAlive(String schedulerName) {
        String key = KEY_PREFIX + schedulerName;
        return redisTemplate.hasKey(key);
    }
}
