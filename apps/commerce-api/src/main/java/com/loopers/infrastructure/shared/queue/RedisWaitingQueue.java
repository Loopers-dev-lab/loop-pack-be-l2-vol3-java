package com.loopers.infrastructure.shared.queue;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.loopers.config.redis.RedisConfig;
import com.loopers.support.queue.WaitingQueue;

/**
 * Redis Sorted Set 기반 대기열 구현체.
 *
 * <p>member를 userId, score를 진입 시각(millis)으로 사용하여 진입 순서를 보장한다.
 * Sorted Set의 특성상 동일 member는 하나만 존재하므로 중복 진입이 자연스럽게 방지된다.</p>
 *
 * <p>Redis 키: {@code queue:waiting} (Sorted Set)</p>
 */
@Component
public class RedisWaitingQueue implements WaitingQueue {

    private static final String QUEUE_KEY = "queue:waiting";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisWaitingQueue(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean enter(Long userId) {
        Boolean added = redisTemplate.opsForZSet()
                .addIfAbsent(QUEUE_KEY, String.valueOf(userId), System.currentTimeMillis());
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Long getPosition(Long userId) {
        return redisTemplate.opsForZSet().rank(QUEUE_KEY, String.valueOf(userId));
    }

    @Override
    public long getTotalCount() {
        Long size = redisTemplate.opsForZSet().zCard(QUEUE_KEY);
        return size != null ? size : 0;
    }
}
