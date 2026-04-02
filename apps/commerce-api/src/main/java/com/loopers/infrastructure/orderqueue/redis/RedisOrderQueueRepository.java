package com.loopers.infrastructure.orderqueue.redis;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.orderqueue.OrderQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisOrderQueueRepository implements OrderQueueRepository {

    private static final String ORDER_QUEUE_KEY = "order:queue:v1";

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void upsert(String memberId, long enteredAt) {
        redisTemplate.opsForZSet().add(ORDER_QUEUE_KEY, memberId, enteredAt);
    }

    @Override
    public Long rank(String memberId) {
        return redisTemplate.opsForZSet().rank(ORDER_QUEUE_KEY, memberId);
    }
}
