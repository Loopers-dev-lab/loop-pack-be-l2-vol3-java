package com.loopers.infrastructure.queue;

import com.loopers.application.queue.OrderQueueWriter;
import com.loopers.config.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisOrderQueueWriter implements OrderQueueWriter {

    private final RedisTemplate<String, String> masterRedisTemplate;

    public RedisOrderQueueWriter(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Override
    public void setEnabled(boolean enabled) {
        String value = String.valueOf(enabled);
        masterRedisTemplate.opsForValue().set(OrderQueueConstants.ORDER_QUEUE_ENABLED_KEY, value);
        masterRedisTemplate.convertAndSend(OrderQueueConstants.ORDER_QUEUE_CHANNEL, value);
    }
}
