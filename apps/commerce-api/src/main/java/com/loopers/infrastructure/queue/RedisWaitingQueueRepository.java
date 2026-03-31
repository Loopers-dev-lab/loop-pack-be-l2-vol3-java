package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.WaitingQueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class RedisWaitingQueueRepository implements WaitingQueueRepository {

    private static final String WAITING_QUEUE_KEY_PREFIX = "queue:waiting:";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisWaitingQueueRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean addIfAbsent(String eventId, Long userId, long score) {
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(waitingKey(eventId), member(userId), score);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Optional<Long> findRank(String eventId, Long userId) {
        Long rank = redisTemplate.opsForZSet().rank(waitingKey(eventId), member(userId));
        return Optional.ofNullable(rank);
    }

    @Override
    public long countWaiting(String eventId) {
        Long size = redisTemplate.opsForZSet().zCard(waitingKey(eventId));
        return size == null ? 0L : size;
    }

    private String waitingKey(String eventId) {
        return WAITING_QUEUE_KEY_PREFIX + eventId;
    }

    private String member(Long userId) {
        return String.valueOf(userId);
    }
}

