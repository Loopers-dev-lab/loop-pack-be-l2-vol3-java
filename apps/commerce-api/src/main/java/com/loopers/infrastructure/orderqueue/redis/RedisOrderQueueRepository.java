package com.loopers.infrastructure.orderqueue.redis;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.orderqueue.OrderQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

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

    @Override
    public long size() {
        Long size = redisTemplate.opsForZSet().zCard(ORDER_QUEUE_KEY);
        return size == null ? 0L : size;
    }

    @Override
    public List<String> peek(int limit) {
        Set<String> members = redisTemplate.opsForZSet().range(ORDER_QUEUE_KEY, 0, limit - 1L);
        return members == null ? List.of() : List.copyOf(members);
    }

    @Override
    public void remove(String memberId) {
        redisTemplate.opsForZSet().remove(ORDER_QUEUE_KEY, memberId);
    }
}
