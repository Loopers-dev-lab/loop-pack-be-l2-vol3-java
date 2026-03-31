package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.WaitingQueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @Override
    public List<Long> popOldest(String eventId, long count) {
        // ZPOPMIN: 가장 작은 값(점수);을 가진 멤버를 제거하고 반환
        // 키 규칙: queue:waiting:{eventId}
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(waitingKey(eventId), count);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        return tuples.stream()
            .map(ZSetOperations.TypedTuple::getValue)
            .filter(value -> value != null && !value.isBlank())
            .map(Long::parseLong)
            .toList();
    }

    private String waitingKey(String eventId) {
        return WAITING_QUEUE_KEY_PREFIX + eventId;
    }

    private String member(Long userId) {
        return String.valueOf(userId);
    }
}

