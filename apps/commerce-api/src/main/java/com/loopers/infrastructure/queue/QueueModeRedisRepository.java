package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueMode;
import com.loopers.domain.queue.QueueModeRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;

@Repository
public class QueueModeRedisRepository implements QueueModeRepository {

    private static final String KEY = "queue:mode";

    private final RedisTemplate<String, String> redisTemplate;

    public QueueModeRedisRepository(
            @Qualifier(REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public QueueMode getCurrentMode() {
        String value = redisTemplate.opsForValue().get(KEY);
        if (value == null) {
            return QueueMode.BYPASS;
        }
        return QueueMode.valueOf(value);
    }

    @Override
    public void updateMode(QueueMode mode) {
        redisTemplate.opsForValue().set(KEY, mode.name());
    }
}
