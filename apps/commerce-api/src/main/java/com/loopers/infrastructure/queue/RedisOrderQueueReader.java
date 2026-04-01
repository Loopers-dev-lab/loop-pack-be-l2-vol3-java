package com.loopers.infrastructure.queue;

import com.loopers.application.queue.OrderQueueReader;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisOrderQueueReader implements OrderQueueReader {

    private static final String ORDER_QUEUE_ENABLED_KEY = "order:queue:enabled";

    private final RedisTemplate<String, String> defaultRedisTemplate;

    public RedisOrderQueueReader(RedisTemplate<String, String> defaultRedisTemplate) {
        this.defaultRedisTemplate = defaultRedisTemplate;
    }

    @Override
    public boolean isEnabled() {
        String value = defaultRedisTemplate.opsForValue().get(ORDER_QUEUE_ENABLED_KEY);
        return "true".equals(value);
    }
}
