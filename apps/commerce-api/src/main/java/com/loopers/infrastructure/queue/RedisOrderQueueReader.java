package com.loopers.infrastructure.queue;

import com.loopers.application.queue.OrderQueueReader;
import com.loopers.config.redis.RedisConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisOrderQueueReader implements OrderQueueReader {

    private final RedisTemplate<String, String> masterRedisTemplate;

    private volatile boolean enabled = false;

    public RedisOrderQueueReader(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @PostConstruct
    void init() {
        String value = masterRedisTemplate.opsForValue().get(OrderQueueConstants.ORDER_QUEUE_ENABLED_KEY);
        this.enabled = "true".equals(value);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    void updateEnabled(boolean value) {
        this.enabled = value;
    }
}
