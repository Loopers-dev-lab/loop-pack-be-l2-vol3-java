package com.loopers.infrastructure.waitingroom;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.waitingroom.WaitingEntry;
import com.loopers.domain.waitingroom.WaitingQueue;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

@Component
public class WaitingQueueRedis implements WaitingQueue {

    private static final String QUEUE_KEY = "queue:waiting";
    private final RedisTemplate<String, String> redisTemplate;

    public WaitingQueueRedis(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean enter(Long userId) {
        Boolean added = redisTemplate.opsForZSet()
                .addIfAbsent(QUEUE_KEY, String.valueOf(userId), System.currentTimeMillis());
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Long getRank(Long userId) {
        return redisTemplate.opsForZSet().rank(QUEUE_KEY, String.valueOf(userId));
    }

    @Override
    public long getTotalWaiting() {
        Long size = redisTemplate.opsForZSet().zCard(QUEUE_KEY);
        return size != null ? size : 0;
    }

    @Override
    public boolean cancel(Long userId) {
        Long removed = redisTemplate.opsForZSet().remove(QUEUE_KEY, String.valueOf(userId));
        return removed != null && removed > 0;
    }

    @Override
    public List<Long> popFront(int count) {
        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(QUEUE_KEY, count);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        return tuples.stream()
                .map(tuple -> Long.parseLong(tuple.getValue()))
                .toList();
    }

    @Override
    public List<WaitingEntry> popFrontWithScores(int count) {
        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(QUEUE_KEY, count);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        return tuples.stream()
                .map(tuple -> new WaitingEntry(
                        Long.parseLong(tuple.getValue()),
                        tuple.getScore().longValue()))
                .toList();
    }
}
